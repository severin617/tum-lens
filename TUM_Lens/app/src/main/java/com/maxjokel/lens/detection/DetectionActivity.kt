/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.maxjokel.lens.detection

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButtonToggleGroup
import com.maxjokel.lens.R
import com.maxjokel.lens.classification.ClassificationActivity
import com.maxjokel.lens.helpers.App
import com.maxjokel.lens.helpers.App.Companion.context
import com.maxjokel.lens.helpers.ImageUtils.getTransformationMatrix
import com.maxjokel.lens.helpers.ImageUtils.saveBitmap
import com.maxjokel.lens.helpers.Logger
import com.maxjokel.lens.helpers.ModelDetectConfig
import com.maxjokel.lens.helpers.Recognition
import com.maxjokel.lens.sign.SignLanguageActivity
import java.io.IOException
import java.util.*

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
class DetectionActivity : CameraActivity(), OverlayView.DrawCallback {
    private var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null
    private var detector: Detector? = null
    private var lastProcessingTimeMs: Long = 0
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var computingDetection = false
    private var timestamp: Long = 0
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null
    private var tracker: MultiBoxTracker? = null

    // Buttons from toggle button group
    private lateinit var analysisToggleGroup: MaterialButtonToggleGroup
    private lateinit var btnDetection: Button
    private lateinit var btnClassification: Button
    private lateinit var btnSignLanguage: Button

    private lateinit var delayText: TextView
    private lateinit var delayButton: Button
    private lateinit var delayEditText: EditText
    private var delayTime = 0

    override val layoutId = R.layout.activity_detection_cam_fragment_tracking
    override val desiredPreviewFrameSize = DESIRED_PREVIEW_SIZE

    private lateinit var prefs: SharedPreferences
    var prefEditor: SharedPreferences.Editor? = null

    public override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
        tracker = MultiBoxTracker(this)
        var cropSize = TF_OD_API_INPUT_SIZE
        try {
            Log.d("modelFileName ", TF_OD_API_MODEL_FILE)
            Log.d("modelLabelName ", TF_OD_API_LABELS_FILE)
            Log.d("ModelInputSize ", TF_OD_API_INPUT_SIZE.toString())
            Log.d("modelQuantized ", TF_OD_API_IS_QUANTIZED.toString())
            detector = TFLiteObjectDetectionAPIModel.create(this, TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE, TF_OD_API_IS_QUANTIZED)
            cropSize = TF_OD_API_INPUT_SIZE
        } catch (e: IOException) {
            e.printStackTrace()
            LOGGER.e(e, "Exception initializing Detector!")
            val toast = Toast.makeText(
                applicationContext, "Detector could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }
        if (size != null) {
            previewWidth = size.width
            previewHeight = size.height
        }
        sensorOrientation = rotation - screenOrientation
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation)
        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight)
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        frameToCropTransform = getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation!!, MAINTAIN_ASPECT
        )
        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)
        trackingOverlay = findViewById<View>(R.id.tracking_overlay) as OverlayView
        trackingOverlay!!.addCallback(this)
        tracker!!.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation!!)
    }

    override fun processImage() {

        ++timestamp
        val currTimestamp = timestamp
        trackingOverlay!!.postInvalidate()

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage()
            return
        }
        Handler().postDelayed({
            computingDetection = true
            LOGGER.i("Preparing image $currTimestamp for detection in bg thread.")
            rgbFrameBitmap!!.setPixels(getConvertedRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight)
            readyForNextImage()
            val canvas = Canvas(croppedBitmap!!)
            canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
            // For examining the actual TF input.
            if (SAVE_PREVIEW_BITMAP) {
                saveBitmap(croppedBitmap!!)
            }
            runInBackground {
                LOGGER.i("Running detection on image $currTimestamp")
                val startTime = SystemClock.uptimeMillis()
//            Log.d("modelFileName1 ", TF_OD_API_MODEL_FILE)
//            Log.d("modelLabelName1 ", TF_OD_API_LABELS_FILE)
//            Log.d("ModelInputSize1 ", TF_OD_API_INPUT_SIZE.toString())
//            Log.d("modelQuantized1 ", TF_OD_API_IS_QUANTIZED.toString())
//            Log.d("modelfile", "modelfile is $modelFile")
                if (modelFile != TF_OD_API_MODEL_FILE) {
                    runOnUiThread {
                        finish()
                        startActivity(getIntent())
                        overridePendingTransition(0, 0)
                        modelFile = TF_OD_API_MODEL_FILE
                    }
                } else {
                    val results = detector!!.recognizeImage(croppedBitmap)
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
                    val tmpBitmap = cropCopyBitmap
                    val canvas = if (tmpBitmap != null) {
                        Canvas(tmpBitmap)
                    } else {
                        null
                    }
                    val paint = Paint()
                    paint.color = Color.RED
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.0f
                    var minimumConfidence = when (MODE) {
                        DetectorMode.TF_OD_API -> MINIMUM_CONFIDENCE_TF_OD_API
                    }
                    val mappedRecognitions: MutableList<Recognition> = ArrayList()
                    if (results != null) {
                        for (result in results) {
                            val location = result?.location
                            if (location != null && result.confidence!! >= minimumConfidence) {
                                canvas?.drawRect(location, paint)
                                cropToFrameTransform!!.mapRect(location)
                                result.location = location
                                mappedRecognitions.add(result)
                            }
                        }
                    }
                    tracker!!.trackResults(mappedRecognitions, currTimestamp)
                    trackingOverlay!!.postInvalidate()
                    computingDetection = false
                    runOnUiThread {
                        showFrameInfo(previewWidth.toString() + "x" + previewHeight)
                        if (canvas != null) {
                            showCropInfo(canvas.width.toString() + "x" + canvas.height)
                        }
                        showInference(lastProcessingTimeMs.toString() + "ms")
                    }
                }
            }
        }, (delayTime*1000).toLong())
    }

    // Which detection model to use: by default uses TF Object Detection API frozen checkpoints.
    private enum class DetectorMode { TF_OD_API }

    override fun setUseNNAPI(isChecked: Boolean) {
        runInBackground {
            try {
                detector!!.setUseNNAPI(isChecked)
            } catch (e: UnsupportedOperationException) {
                LOGGER.e(e, "Failed to set \"Use NNAPI\".")
                runOnUiThread { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun setNumThreads(numThreads: Int) {
        runInBackground { detector!!.setNumThreads(numThreads) }
    }

    override fun drawCallback(canvas: Canvas?) {
        tracker!!.draw(canvas!!)
        if (isDebug) {
            tracker!!.drawDebug(canvas)
        }
    }

    @SuppressLint("SetTextI18n")
    protected fun updateDelayTime() {

        // save number of threads to sharedPreferences
        prefEditor!!.putInt("delay_detector", delayTime)
        prefEditor!!.apply()

        // update UI
        delayText.text = "delay time is $delayTime seconds"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        analysisToggleGroup = findViewById(R.id.analysisToggleGroup)
        btnClassification = findViewById(R.id.btn_classification)
        btnDetection = findViewById(R.id.btn_detection)
        btnSignLanguage = findViewById(R.id.btn_sign_language);

        delayText = findViewById(R.id.delay_text_detector)
        delayButton = findViewById(R.id.delay_time_button_detector)
        delayEditText = findViewById(R.id.delay_time_edit_text_detector)

        prefs = this.getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)
        prefEditor = prefs!!.edit()

        val savedDelayTime = prefs!!.getInt("delay_detector", 0)
        delayTime = savedDelayTime
        delayText.text = "delay time is $delayTime seconds"

        delayButton.setOnClickListener(View.OnClickListener {
            if (delayEditText.text.isNotEmpty()) {
                delayTime = Integer.parseInt(delayEditText.text.toString())
                delayEditText.text.clear()
                updateDelayTime()
            } else {
                Toast.makeText(context, "You did not enter the delay time needed!", Toast.LENGTH_SHORT).show()
            }
        })

        analysisToggleGroup.addOnButtonCheckedListener { group, checkedId, _ ->
            if (checkedId == btnClassification.id) {
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                val intent = Intent(this, ClassificationActivity::class.java)
                startActivity(intent)
                finish()
            }
            if (checkedId == btnSignLanguage.id) {
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                val intent = Intent(this, SignLanguageActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // check() also unchecks all other buttons because group is defined in single selection mode
        analysisToggleGroup.check(R.id.btn_detection)
    }

    fun initialize () {
        Companion.prefs = App.context!!.getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)
        Log.d("Initialize", "In the initialize in DetectionActivity called from ModelSelectorDetectionFragment")
        val id = Companion.prefs.getInt("model_detection", 0)
        Log.d("ModelDetectionID", "Model Detection ID from prefs is $id")
        val listModels: List<ModelDetectConfig> = ListSingletonDetection.modelConfigs
        if (id == 0) {
            TF_OD_API_INPUT_SIZE = 300
            TF_OD_API_IS_QUANTIZED = true
            TF_OD_API_MODEL_FILE = "detect.tflite"
            TF_OD_API_LABELS_FILE = "labelmap.txt"
            modelFile = TF_OD_API_MODEL_FILE
            return
        }
        for (m in listModels) {
            if (id == m.modelId) {
                TF_OD_API_INPUT_SIZE = m.inputSize
                TF_OD_API_IS_QUANTIZED = m.quantized
                TF_OD_API_MODEL_FILE = m.modelFilename.toString()
                TF_OD_API_LABELS_FILE = m.labelFilename.toString()
                modelFile = TF_OD_API_MODEL_FILE
                return
            }
        }
    }

    fun reInitialize () {
        Companion.prefs = App.context!!.getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)
        Log.d("Initialize", "In the reInitialize in DetectionActivity called from ModelSelectorDetectionFragment")
        val id = Companion.prefs.getInt("model_detection", 0)
        Log.d("ModelDetectionID", "Model Detection ID from prefs is $id")
        val listModels: List<ModelDetectConfig> = ListSingletonDetection.modelConfigs
        if (id == 0) {
            TF_OD_API_INPUT_SIZE = 300
            TF_OD_API_IS_QUANTIZED = true
            TF_OD_API_MODEL_FILE = "detect.tflite"
            TF_OD_API_LABELS_FILE = "labelmap.txt"
            return
        }
        for (m in listModels) {
            if (id == m.modelId) {
                TF_OD_API_INPUT_SIZE = m.inputSize
                TF_OD_API_IS_QUANTIZED = m.quantized    
                TF_OD_API_MODEL_FILE = m.modelFilename.toString()
                TF_OD_API_LABELS_FILE = m.labelFilename.toString()
                return
            }
        }
    }


    companion object {
        private val LOGGER = Logger()
        private lateinit var prefs: SharedPreferences

        // Configuration values for the prepackaged SSD model.
        private var TF_OD_API_INPUT_SIZE = 0
        private var TF_OD_API_IS_QUANTIZED = false
        private var TF_OD_API_MODEL_FILE = ""
        private var TF_OD_API_LABELS_FILE = ""
        private var modelFile: String = ""

        private val MODE = DetectorMode.TF_OD_API
    
        // Minimum detection confidence to track a detection.
        private const val MINIMUM_CONFIDENCE_TF_OD_API = 0.5f

        private const val MAINTAIN_ASPECT = false
        private val DESIRED_PREVIEW_SIZE = Size(1440, 720)
        private const val SAVE_PREVIEW_BITMAP = false

    }
}
