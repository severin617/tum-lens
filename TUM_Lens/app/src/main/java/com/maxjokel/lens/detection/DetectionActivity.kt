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

import android.content.Intent
import android.graphics.*
import android.media.ImageReader
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.google.android.material.button.MaterialButtonToggleGroup
import com.maxjokel.lens.helpers.ImageUtils.getTransformationMatrix
import com.maxjokel.lens.helpers.ImageUtils.saveBitmap
import com.maxjokel.lens.helpers.Logger
import java.io.IOException
import java.util.*
import com.maxjokel.lens.R
import com.maxjokel.lens.classification.ClassificationActivity

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
    private var borderedText: BorderedText? = null

    override val layoutId = R.layout.activity_detection_cam_fragment_tracking
    override val desiredPreviewFrameSize = DESIRED_PREVIEW_SIZE

    public override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        borderedText = BorderedText(textSizePx)
        borderedText!!.setTypeface(Typeface.MONOSPACE)
        tracker = MultiBoxTracker(this)
        var cropSize = TF_OD_API_INPUT_SIZE
        try {
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
            var minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API
            minimumConfidence = when (MODE) {
                DetectorMode.TF_OD_API -> MINIMUM_CONFIDENCE_TF_OD_API
            }
            val mappedRecognitions: MutableList<Detector.Recognition> = ArrayList()
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

    override fun onBackPressed() {
        val intent = Intent(this@DetectionActivity, ClassificationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btnDetectionModeToggle = findViewById<MaterialButtonToggleGroup>(R.id.analysisToggleGroup)
        val btnClassification = findViewById<Button>(R.id.btn_classification)
        btnDetectionModeToggle.addOnButtonCheckedListener { group, checkedId, _ ->
            if (checkedId == btnClassification.id) {
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                val intent = Intent(this, ClassificationActivity::class.java)
                // TODO: add AcitivtyOptions (see same fun in ClassificationActivity) for smooth transition
                startActivity(intent)
            }
        }
    }


    companion object {
        private val LOGGER = Logger()

        // Configuration values for the prepackaged SSD model.
        private const val TF_OD_API_INPUT_SIZE = 300
        private const val TF_OD_API_IS_QUANTIZED = true
        private const val TF_OD_API_MODEL_FILE = "detect.tflite"
        private const val TF_OD_API_LABELS_FILE = "labelmap.txt"
        private val MODE = DetectorMode.TF_OD_API

        // Minimum detection confidence to track a detection.
        private const val MINIMUM_CONFIDENCE_TF_OD_API = 0.5f

        private const val MAINTAIN_ASPECT = false
        private val DESIRED_PREVIEW_SIZE = Size(640, 240)
        private const val SAVE_PREVIEW_BITMAP = false
        private const val TEXT_SIZE_DIP = 10f
    }
}
