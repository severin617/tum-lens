package com.maxjokel.lens.sign

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.mediapipe.components.CameraHelper.CameraFacing
import com.google.mediapipe.components.CameraXPreviewHelper
import com.google.mediapipe.components.ExternalTextureConverter
import com.google.mediapipe.components.FrameProcessor
import com.google.mediapipe.formats.proto.ClassificationProto
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import com.google.mediapipe.framework.AndroidAssetUtil
import com.google.mediapipe.framework.Packet
import com.google.mediapipe.framework.PacketGetter
import com.google.mediapipe.framework.ProtoUtil
import com.google.mediapipe.glutil.EglManager
import com.maxjokel.lens.R
import com.maxjokel.lens.classification.ClassificationActivity
import com.maxjokel.lens.detection.DetectionActivity
import com.maxjokel.lens.fragments.*
import com.maxjokel.lens.helpers.Recognition
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async


class SignLanguageActivity : AppCompatActivity() {

    // Mediapipe constants
    private val FLIP_FRAMES_VERTICALLY: Boolean = true
    private val NUM_BUFFERS = 2

    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private var processor: FrameProcessor? = null

    // Handles camera access via the {@link CameraX} Jetpack support library.
    private var cameraHelper: CameraXPreviewHelper? = null

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private var previewFrameTexture: SurfaceTexture? = null

    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private var previewDisplayView: SurfaceView? = null

    // Creates and manages an {@link EGLContext}.
    private var eglManager: EglManager? = null

    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private var converter: ExternalTextureConverter? = null

    // ApplicationInfo for retrieving metadata defined in the manifest.
    private lateinit var applicationInfos: ApplicationInfo

    // stream of classifications
    private val OUTPUT_CLASSIFICATION_STREAM = "classifications"
    private var cameraOpens = 0


    // Buttons from toggle button group
    private lateinit var analysisToggleGroup: MaterialButtonToggleGroup
    private lateinit var btnDetection: Button
    private lateinit var btnClassification: Button
    private lateinit var btnSignLanguage: Button

    // Fragments
    private var predictionsFragment: SignPredictionsFragment? = null

    // progress
    //private lateinit var progress: RelativeLayout

    private val DEBUG_TAG = "sign_debug"

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onCreate()")
        super.onCreate(savedInstanceState)
        ProtoUtil.registerTypeName(
            ClassificationProto.ClassificationList::class.java,
            "mediapipe.ClassificationList"
        )
        setContentView(R.layout.activity_sign_language)

        try {
            applicationInfos = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity got meta data")
        } catch (e: PackageManager.NameNotFoundException) {
            applicationInfos = ApplicationInfo()
            applicationInfos.metaData.putString("binaryGraphName", "sign_translating_gpu.binarypb")
            applicationInfos.metaData.putString("inputVideoStreamName", "input_video")
            applicationInfos.metaData.putString("outputVideoStreamName", "output_video")
            applicationInfos.metaData.putBoolean("flipFramesVertically", true)
            applicationInfos.metaData.putBoolean("cameraFacingFront", false)
            applicationInfos.metaData.putInt("converterNumBuffers", 3)
            Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity could not get meta data from AndroidManifest")
        }

        previewDisplayView = SurfaceView(this)
        setupPreviewDisplayView()

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this)
        eglManager = EglManager(null)
        processor = FrameProcessor(
            this,
            eglManager!!.nativeContext,
            applicationInfos.metaData.getString("binaryGraphName"),
            applicationInfos.metaData.getString("inputVideoStreamName"),
            applicationInfos.metaData.getString("outputVideoStreamName")
        )
        processor!!
            .videoSurfaceOutput
            .setFlipY(
                applicationInfos.metaData.getBoolean(
                    "flipFramesVertically",
                    FLIP_FRAMES_VERTICALLY
                )
            )

        analysisToggleGroup = findViewById(R.id.analysisToggleGroup)
        btnClassification = findViewById(R.id.btn_classification)
        btnDetection = findViewById(R.id.btn_detection)
        btnSignLanguage = findViewById(R.id.btn_sign_language)

        analysisToggleGroup.addOnButtonCheckedListener { group, checkedId, _ ->
            if (checkedId == btnDetection.id) {
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                val intent = Intent(this, DetectionActivity::class.java)
                startActivity(intent)
                finish()
            }
            if (checkedId == btnClassification.id){
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                val intent = Intent(this, ClassificationActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +             SETUP BOTTOM SHEET FRAGMENTS              +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init new Fragment Instances
        val msf = ModelSelectorFragment.newInstance()
        predictionsFragment = SignPredictionsFragment.newInstance()

        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.add(
            R.id.perframe_results_container_sign, predictionsFragment!!,
            "predictionsFragment"
        )
        fragmentTransaction.commit()

        //progress = findViewById(R.id.loadingPanel)

        GlobalScope.async {
            Log.println(Log.DEBUG, DEBUG_TAG,"${Thread.currentThread()} has run COROUTINE.")
            Thread.sleep(1_500)
            startCamera()
            //progress.visibility = View.GONE
        }

        // classification callback
        processor!!.addPacketCallback(
            OUTPUT_CLASSIFICATION_STREAM
        ) {
                packet: Packet? ->
            val classifications = PacketGetter.getProto(packet, ClassificationProto.ClassificationList.getDefaultInstance())
            val results = arrayListOf<Recognition>()
            for(c in classifications.classificationList){
                results.add(Recognition(""+c.index, c.label, c.score, null))
                Log.println(Log.DEBUG, DEBUG_TAG, c.toString())
            }
            this.
            runOnUiThread {
                predictionsFragment!!.showRecognitionResults(results, 0)
            }
        }

        //processor!!.setAsynchronousErrorListener { _ -> startCamera() }
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onCreate() finished")
    }

    private fun setupPreviewDisplayView() {
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity setupPreviewDisplayView()")
        previewDisplayView!!.visibility = View.GONE
        val viewGroup = findViewById<ViewGroup>(R.id.sign_language_camera)
        viewGroup.addView(previewDisplayView)
        previewDisplayView!!
            .holder
            .addCallback(
                object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onSurfaceCreated()")
                        processor!!.videoSurfaceOutput.setSurface(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onSurfaceChanged()")
                        onPreviewDisplaySurfaceChanged(holder, format, width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onSurfaceDestroyed()")
                        processor!!.videoSurfaceOutput.setSurface(null)
                    }
                })
    }

    private fun onPreviewDisplaySurfaceChanged(
        holder: SurfaceHolder?, format: Int, width: Int, height: Int
    ) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onPreviewDisplaySurfaceChanged()")
        val viewSize: Size = computeViewSize(width, height)
        val displaySize: Size = cameraHelper!!.computeDisplaySizeFromViewSize(viewSize)
        val isCameraRotated = cameraHelper!!.isCameraRotated

        // Configure the output width and height as the computed display size.
        converter!!.setDestinationSize(
            if (isCameraRotated) displaySize.height else displaySize.width,
            if (isCameraRotated) displaySize.width else displaySize.height
        )
    }

    private fun computeViewSize(width: Int, height: Int): Size {
        return Size(width, height)
    }

    private fun cameraTargetResolution(): Size? {
        return null // No preference and let the camera (helper) decide.
    }

    private fun onCameraStarted(surfaceTexture: SurfaceTexture) {
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onCameraStarted()")
        previewFrameTexture = surfaceTexture
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView!!.visibility = View.VISIBLE
    }

    private fun startCamera() {
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity startCamera()")
        if(cameraOpens > 1){
            return
        }
        cameraOpens++
        cameraHelper = CameraXPreviewHelper()
        previewFrameTexture = converter!!.surfaceTexture
        cameraHelper!!.setOnCameraStartedListener { surfaceTexture: SurfaceTexture? ->
            onCameraStarted(
                surfaceTexture!!
            )
        }
        val cameraFacing = if (applicationInfos.metaData.getBoolean(
                "cameraFacingFront",
                false
            )
        ) CameraFacing.FRONT else CameraFacing.BACK
        cameraHelper!!.startCamera(
            this, cameraFacing, previewFrameTexture, cameraTargetResolution()
        )
    }

    override fun onResume() {
        super.onResume()
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onResume()")
        converter = ExternalTextureConverter(
            eglManager!!.context,
            applicationInfos.metaData.getInt("converterNumBuffers", NUM_BUFFERS)
        )
        converter!!.setFlipY(
            applicationInfos.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY)
        )
        converter!!.setConsumer(processor)
        //if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera()
       // }
    }

    override fun onPause() {
        super.onPause()
        Log.println(Log.DEBUG, DEBUG_TAG, "Sign Activity onPause()")
        converter!!.close()

        // Hide preview display until we re-open the camera again.
        previewDisplayView!!.visibility = View.GONE
    }

    companion object{
        init {
            System.loadLibrary("mediapipe_jni")
            try {
                System.loadLibrary("opencv_java3")
            } catch (e: UnsatisfiedLinkError ) {
                System.loadLibrary("opencv_java4")
            }
        }
    }

}