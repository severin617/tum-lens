package com.maxjokel.lens

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import android.util.Size
import android.view.*
import android.view.GestureDetector.OnDoubleTapListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.google.common.util.concurrent.ListenableFuture
import com.maxjokel.lens.Classifier.Companion.recognizeImage
import com.maxjokel.lens.fragments.*
import com.maxjokel.lens.helpers.CameraEvents
import com.maxjokel.lens.helpers.FreezeAnalyzer
import com.maxjokel.lens.helpers.FreezeCallback
import com.maxjokel.lens.helpers.ImageUtils.toCroppedBitmap
import com.maxjokel.lens.helpers.Logger
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ViewFinder : AppCompatActivity(), GestureDetector.OnGestureListener, OnDoubleTapListener,
    FreezeCallback, CameraEvents {
    // CameraX related:   [source: https://developer.android.com/training/camerax/preview#java]
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var _cameraProvider: ProcessCameraProvider? = null
    private var _cameraSelector: CameraSelector? = null
    private var _camera: Camera? = null
    private var _preview: Preview? = null // CameraX use cases for live preview feed
    private var _analysis: ImageAnalysis? = null // CameraX use cases for actual classification
    private var _cameraExecutorForAnalysis: ExecutorService? = null
    private var _cameraExecutorForFreezing: ExecutorService? = null
    private var isFlashEnabled = false

    // Layout element
    private var _viewFinder: PreviewView? = null
    private var _frozenPreviewWindow: ImageView? = null
    private var _freezeAnalyzer: FreezeAnalyzer? = null
    private var _freezeImageAnalysis: ImageAnalysis? = null
    var lensFrontBack = 0 // [0 = back, 1 = front]

    // TF-Lite related to CLASSIFICATION:   [source: TF-Lite example app]
    private var previewDimX = 960
    private var previewDimY = 1280
    private var startTimestamp: Long = 0
    private var isCurrentlyClassifying = false
    private var isClassificationPaused = false

    // 'double tap' gesture
    private var mGestureDetector: GestureDetectorCompat? = null

    // instantiate new SharedPreferences object
    var prefs: SharedPreferences? = null
    var prefEditor: SharedPreferences.Editor? = null
    var predictionsFragment: PredictionsFragment? = null
    var smoothedPredictionsFragment: SmoothedPredictionsFragment? = null

    // please note: the static classifier class is instantiated in 'ModelSelectorFragment'
    override fun onCreate(savedInstanceState: Bundle?) {
        LOGGER.i("+++ Hello from ViewFinder.java +++")
        super.onCreate(savedInstanceState)

        // load sharedPreferences object and set up editor
        prefs = getSharedPreferences("TUM_Lens_Prefs", MODE_PRIVATE)

        // prevent display from being dimmed down
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // set status bar background to black
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)

        // set corresponding layout
        setContentView(R.layout.activity_view_finder)

        // set up gesture detection   [source: https://developer.android.com/training/gestures/detector#java]
        // Instantiate the gesture detector with the application context
        mGestureDetector = GestureDetectorCompat(this, this)

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +             SETUP BOTTOM SHEET FRAGMENTS              +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init new Fragment Instances
        val msf = ModelSelectorFragment.newInstance()
        predictionsFragment = PredictionsFragment.newInstance()
        smoothedPredictionsFragment = SmoothedPredictionsFragment.newInstance()
        val cameraSettingsFragment = CameraSettingsFragment()
        val threadNumberFragment = ThreadNumberFragment()
        val processingUnitSelectorFragment = ProcessingUnitSelectorFragment()
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.add(R.id.modelselector_container, msf, "msf")
        fragmentTransaction.add(
            R.id.perframe_results_container,
            predictionsFragment!!,
            "predictionsFragment"
        )
        fragmentTransaction.add(
            R.id.smoothed_results_container,
            smoothedPredictionsFragment!!,
            "smoothedPredictionsFragment"
        )
        fragmentTransaction.add(
            R.id.camera_settings_container,
            cameraSettingsFragment,
            "cameraSettingsFragment"
        )
        fragmentTransaction.add(
            R.id.thread_number_container,
            threadNumberFragment,
            "threadNumberFragment"
        )
        fragmentTransaction.add(
            R.id.processing_unit_container,
            processingUnitSelectorFragment,
            "processingUnitSelectorFragment"
        )
        fragmentTransaction.commit()

        // add ViewFinder to list of event listeners in cameraSettingsFragment, in order to get
        // notified when the user toggles the camera or flash
        cameraSettingsFragment.addListener(this)

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +           SET UP USER INTERFACE COMPONENTS            +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init view finder that displays the camera output
        _viewFinder = findViewById(R.id.viewFinder)
        _frozenPreviewWindow = findViewById(R.id.frozen_preview)

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                  INIT CORE FEATURES                   +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // load lens rotation from SharedPreferences  [0: back, 1: front]
        lensFrontBack = prefs!!.getInt("lens", 0)
        initCameraX()

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                SET UP EVENT LISTENERS                 +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // restart paused classification
        findViewById<View>(R.id.btn_play).setOnClickListener {
            val focusCircle = findViewById<ImageView>(R.id.focus_circle)

            // load 150ms animations
            val fade_in =
                AnimationUtils.loadAnimation(this@ViewFinder, R.anim.basic_fade_in_150) as Animation
            val fade_out = AnimationUtils.loadAnimation(
                this@ViewFinder,
                R.anim.basic_fade_out_150
            ) as Animation
            focusCircle.startAnimation(fade_in)
            focusCircle.visibility = View.VISIBLE
            findViewById<View>(R.id.btn_play).startAnimation(fade_out)
            findViewById<View>(R.id.btn_play).visibility = View.GONE
            findViewById<View>(R.id.view_finder_shadow).animate().alpha(0f).setDuration(150)
                .setListener(null)

            // re-init live camera preview feed
            resetFrozenViewFinder()
            isClassificationPaused = !isClassificationPaused
        }

        // jump to 'camera roll classifier' activity
        findViewById<View>(R.id.btn_camera_roll_activity).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            val intent = Intent(this@ViewFinder, CameraRoll::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            finish()
        }
    }

    // END OF 'onCreate()' METHOD
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // buildPreviewUseCase() Method
    // inits preview object that holds the camera live feed
    // [based around: https://stackoverflow.com/q/59661727]
    private fun buildPreviewUseCase() {
        LOGGER.i("ViewFinder: building preview use case.")

        // init preview object that holds the camera live feed
        _preview = Preview.Builder()
            .setTargetResolution(Size(previewDimX, previewDimY))
            .setTargetRotation(Surface.ROTATION_0) // warum auch immer...
            .build()

        // bind and init camera feed to the corresponding object in our layout
        _preview!!.setSurfaceProvider(_viewFinder!!.createSurfaceProvider())

        // bind preview use case to CameraX lifecycle
        if (_cameraSelector != null) _cameraProvider!!.bindToLifecycle(
            this,
            _cameraSelector!!,
            _preview
        )
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun buildAnalyzerUseCase() {
        LOGGER.i("ViewFinder: building analysis use case.")

        // init analyzer object
        _analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(previewDimX, previewDimY))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // async
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        // init new single thread ExecutorService for the analyzer use case
        _cameraExecutorForAnalysis = Executors.newSingleThreadExecutor()

        // set up the image analysis use case
        _analysis!!.setAnalyzer(_cameraExecutorForAnalysis!!, ImageAnalysis.Analyzer { image ->
            // Logs this method so that it can be analyzed with systrace.
            Trace.beginSection("analyzing")
            //                LOGGER.i("*** ViewFinder: analyze() START *** ");


            // close image if classification is halted or already running
            if (isCurrentlyClassifying || isClassificationPaused) {
                LOGGER.i("*** ViewFinder: closing as (isCurrentlyClassifying || isClassificationPaused) == true ***")
                image.close() // close the image in order to clear the pipeline
                return@Analyzer
            }


            // update the flag in order to block the image pipeline for the duration of the active classification
            isCurrentlyClassifying = true


            // convert Image to Bitmap
            @ExperimentalGetImage val img = image.image
            val rgbBitmap = toCroppedBitmap(img!!, image.imageInfo.rotationDegrees)


            // make sure that the bitmap is not null
            if (rgbBitmap == null) {
                LOGGER.i("*** ViewFinder: closing as Bitmap == NULL ***")
                image.close() // close the image in order to clear the pipeline
                return@Analyzer
            }
            val startTime = SystemClock.uptimeMillis()

            // run inference on image
            val results = recognizeImage(rgbBitmap)
            startTimestamp = SystemClock.uptimeMillis() - startTime


            // pass list of results to fragments that render the recognition results to UI
            runOnUiThread {
                predictionsFragment!!.showRecognitionResults(results, startTimestamp)
                smoothedPredictionsFragment!!.showSmoothedRecognitionResults(results)
            }
            Trace.endSection()

            // close image no matter what to clear the pipeline
            Trace.beginSection("closing image")
            image.close()
            Trace.endSection()

            // now that the classification is done, reset the flag
            isCurrentlyClassifying = false

//                LOGGER.i("*** ViewFinder: analyze() ENDE; received " + results.size() + "  results *** \n");
        } // END of analyze(@NonNull ImageProxy image) - - - - - - - - - - - - - - - - - - - - -
        ) // END of _analysis.setAnalyzer ...  - - - - - - - - - - - - - - - - - - - - - - - - - -

        // bind analysis use case to CameraX lifecycle
        if (_cameraSelector != null) _camera =
            _cameraProvider!!.bindToLifecycle(this, _cameraSelector!!, _analysis)
    }

    // buildFreezeUseCase()
    //
    // freeze last frame when classification is halted;
    // based on: https://stackoverflow.com/a/59674075
    //
    private fun buildFreezeUseCase() {
        LOGGER.i("ViewFinder: building freeze use case.")

        // init analysis object for processing last frame
        _freezeImageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(previewDimX, previewDimY))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        // init new single thread ExecutorService for the analyzer use case
        _cameraExecutorForFreezing = Executors.newSingleThreadExecutor()

        // activate
        _freezeAnalyzer = FreezeAnalyzer(this)
        _freezeImageAnalysis!!.setAnalyzer(_cameraExecutorForFreezing!!, _freezeAnalyzer!!)

        // bind 'freeze' use case to CameraX lifecycle
        if (_cameraSelector != null) _camera =
            _cameraProvider!!.bindToLifecycle(this, _cameraSelector!!, _freezeImageAnalysis)
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // initCameraX()
    //
    // initializes a new 'CameraX' instance, including preview and two analysis use cases
    //
    private fun initCameraX() {
        LOGGER.i("ViewFinder: initializing CameraX.")
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture!!.addListener({
            try {
                // init new camera provider
                _cameraProvider = cameraProviderFuture!!.get()


                // select lens by initializing a new camera selector
                _cameraSelector = if (lensFrontBack == 1) { // FRONT FACING
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()
                } else { // BACK FACING
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                }


                // init preview use case that holds the camera live feed
                buildPreviewUseCase()

                // init analysis use case that converts each frame to bitmap and passes it on for inference
                buildAnalyzerUseCase()

                // init use case for 'freezing' the last frame when classification is halted
                buildFreezeUseCase()


                // set up auto focus   [source: https://developer.android.com/training/camerax/configuration#java]
                // (we just want the view finder center to be in focus)
                val cameraControl = _camera!!.cameraControl
                val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                    previewDimX.toFloat(),
                    previewDimY.toFloat()
                )
                val point: MeteringPoint = factory.createPoint(
                    (0.5 * previewDimX).toInt().toFloat(),
                    (0.5 * previewDimY).toInt().toFloat()
                )
                val focusMeteringAction =
                    FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(2, TimeUnit.SECONDS) //auto-focus every x seconds
                        .build()
                cameraControl.startFocusAndMetering(focusMeteringAction)
                LOGGER.i("ViewFinder: CameraX initialization complete!")
            } catch (exception: InterruptedException) {
                LOGGER.e("Error occurred while setting up CameraX: $exception")
            } catch (exception: ExecutionException) {
                LOGGER.e("Error occurred while setting up CameraX: $exception")
            }
        }, ContextCompat.getMainExecutor(this))
    } // END of initCameraX()  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // START of TOUCH and GESTURE HANDLER section
    //
    // we use Android's GestureDetector class to detect common gestures such as swiping down
    // this function acts as a 'gateway' that passes the MotionEvent on to where it can be processed
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (mGestureDetector!!.onTouchEvent(event)) {
            true
        } else super.onTouchEvent(event)
    }

    // onDoubleTap   -> pauses the classification
    override fun onDoubleTap(e: MotionEvent): Boolean {
        val focusCircle = findViewById<ImageView>(R.id.focus_circle)

        // load 150ms animations
        val fadeIn =
            AnimationUtils.loadAnimation(this@ViewFinder, R.anim.basic_fade_in_150) as Animation
        val fadeOut =
            AnimationUtils.loadAnimation(this@ViewFinder, R.anim.basic_fade_out_150) as Animation
        if (isClassificationPaused) { // resume classification: adjust UI

            // !!! IMPORTANT !!!
            // see 'btn_pause event listener' as well!

            // re-init live camera preview feed
            resetFrozenViewFinder()
            focusCircle.startAnimation(fadeIn)
            focusCircle.visibility = View.VISIBLE
            findViewById<View>(R.id.btn_play).startAnimation(fadeOut)
            findViewById<View>(R.id.btn_play).visibility = View.GONE
            findViewById<View>(R.id.view_finder_shadow).animate().alpha(0f).setDuration(150)
                .setListener(null)
        } else { // pause classification: adjust UI


            // trigger the UI change
            _freezeAnalyzer!!.freeze(lensFrontBack)
            focusCircle.startAnimation(fadeOut)
            focusCircle.visibility = View.GONE
            findViewById<View>(R.id.btn_play).startAnimation(fadeIn)
            findViewById<View>(R.id.btn_play).visibility = View.VISIBLE
            findViewById<View>(R.id.view_finder_shadow).animate().alpha(0.5f).setDuration(150)
                .setListener(null)
        }

        // toggle variable
        isClassificationPaused = !isClassificationPaused
        return false
    }

    // END - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // 'Freeze Callback' Interface
    //
    // onFrozenBitmap() Method
    //
    // [Part 2] of freezing the camera feed when the user pauses the classification
    // [Part 1] -> see 'FreezeAnalyzer.java'
    //
    // this method hides the camera feed preview layout object
    // and instead shows an ImageView, that displays the last active frame, before the classifcation was paused
    override fun onFrozenBitmap(b: Bitmap?) {
        runOnUiThread {
            _frozenPreviewWindow!!.setImageBitmap(b)
            _frozenPreviewWindow!!.visibility = View.VISIBLE
            _viewFinder!!.visibility = View.GONE
        }
    }

    // resetFrozenViewFinder() method
    // counterpart to the method above to restore the default
    fun resetFrozenViewFinder() {
        runOnUiThread {
            _frozenPreviewWindow!!.visibility = View.INVISIBLE
            _viewFinder!!.visibility = View.VISIBLE
        }
    }

    // END - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // 'Camera Events' Interface
    override fun onRotateToggled() {

        // switch lens by changing global variable
        lensFrontBack = if (lensFrontBack == 0) {
            1
        } else {
            0
        }

        // unbind all use cases
        _cameraProvider!!.unbindAll()

        // re-init CameraX
        initCameraX()

        // init Editor and save to SharedPreferences
        prefEditor = prefs!!.edit()
        prefEditor!!.putInt("lens", lensFrontBack)
        prefEditor!!.apply()
    }

    override fun onFlashToggled() {

        // turn camera flash on or off, if there is one
        if (_camera!!.cameraInfo.hasFlashUnit()) {
            val btn = findViewById<ImageButton>(R.id.btn_flash)

            // perform haptic feedback
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            if (isFlashEnabled) {
                _camera!!.cameraControl.enableTorch(false)
                btn.setColorFilter(ContextCompat.getColor(this@ViewFinder, R.color.colorPrimary))
            } else {
                btn.setColorFilter(ContextCompat.getColor(this@ViewFinder, R.color.colorAccent))
                _camera!!.cameraControl.enableTorch(true)
            }
            isFlashEnabled = !isFlashEnabled
        }
    }


    override fun onResume() {
        super.onResume()
        isClassificationPaused = false
    }

    override fun onPause() {
        super.onPause()
        isClassificationPaused = true
    }

    override fun onStop() {
        super.onStop()
        isClassificationPaused = true
    }

    override fun onRestart() {
        super.onRestart()
        isClassificationPaused = false
    }

    override fun onDestroy() {
        super.onDestroy()
        _analysis!!.clearAnalyzer()
        _cameraProvider!!.unbindAll()
        _cameraExecutorForAnalysis!!.shutdownNow()
        _cameraExecutorForFreezing!!.shutdownNow()
    }

    // UNIMPLEMENTED GESTURE METHODS
    override fun onFling(
        event1: MotionEvent,
        event2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return true
    }

    override fun onDown(event: MotionEvent): Boolean {
        return true
    }

    override fun onLongPress(event: MotionEvent) {}
    override fun onScroll(
        event1: MotionEvent,
        event2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return true
    }

    override fun onShowPress(event: MotionEvent) {}
    override fun onSingleTapUp(event: MotionEvent): Boolean {
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return false
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }
    // END OF UNIMPLEMENTED GESTURE METHODS - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    companion object {
        // init new Logger instance
        private val LOGGER = Logger()
    }
}