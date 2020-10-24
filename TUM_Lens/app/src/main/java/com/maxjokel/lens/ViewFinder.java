package com.maxjokel.lens;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Size;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.google.common.util.concurrent.ListenableFuture;
import com.maxjokel.lens.fragments.CameraSettingsFragment;
import com.maxjokel.lens.fragments.ModelSelectorFragment;
import com.maxjokel.lens.fragments.PredictionsFragment;
import com.maxjokel.lens.fragments.ProcessingUnitSelectorFragment;
import com.maxjokel.lens.fragments.SmoothedPredictionsFragment;
import com.maxjokel.lens.fragments.ThreadNumberFragment;
import com.maxjokel.lens.helpers.App;
import com.maxjokel.lens.helpers.CameraEvents;
import com.maxjokel.lens.helpers.FreezeAnalyzer;
import com.maxjokel.lens.helpers.FreezeCallback;
import com.maxjokel.lens.helpers.Recognition;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import helpers.ImageUtils;
import helpers.Logger;

public class ViewFinder extends AppCompatActivity
            implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener,
        FreezeCallback,
        CameraEvents {


    // init new Logger instance
    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // CameraX related:   [source: https://developer.android.com/training/camerax/preview#java]
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture = null;

    private ProcessCameraProvider _cameraProvider = null;

    private CameraSelector _cameraSelector = null;

    private Camera _camera = null;

    private Preview _preview  = null; // CameraX use cases for live preview feed
    private ImageAnalysis _analysis = null; // CameraX use cases for actual classification

    private ExecutorService _cameraExecutorForAnalysis = null;
    private ExecutorService _cameraExecutorForFreezing = null;

    private Boolean isFlashEnabled = false;

    // Layout element
    private PreviewView _viewFinder;
    private ImageView _frozenPreviewWindow;


    private FreezeAnalyzer _freezeAnalyzer = null;
    private ImageAnalysis _freezeImageAnalysis = null;


    int lens_front_back = 0; // [0 = back, 1 = front]


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // TF-Lite related to CLASSIFICATION:   [source: TF-Lite example app]
    protected int previewDimX = 960;
    protected int previewDimY = 1280;

    private long startTimestamp;

    private boolean isCurrentlyClassifying = false;
    private boolean isClassificationPaused = false;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // 'double tap' gesture
    private GestureDetectorCompat mGestureDetector;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // instantiate new SharedPreferences object
    SharedPreferences prefs = null;
    SharedPreferences.Editor prefEditor = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    PredictionsFragment predictionsFragment = null;
    SmoothedPredictionsFragment smoothedPredictionsFragment = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // please note: the static classifier class is instantiated in 'ModelSelectorFragment'

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        LOGGER.i("+++ Hello from ViewFinder.java +++");

        super.onCreate(savedInstanceState);

        // load sharedPreferences object and set up editor
        prefs = getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);


        // prevent display from being dimmed down
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set status bar background to black
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.black));


        // set corresponding layout
        setContentView(R.layout.activity_view_finder);


        // set up gesture detection   [source: https://developer.android.com/training/gestures/detector#java]
        // Instantiate the gesture detector with the application context
        mGestureDetector = new GestureDetectorCompat(this,this);





        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +             SETUP BOTTOM SHEET FRAGMENTS              +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +


        // init new Fragment Instances
        ModelSelectorFragment msf = ModelSelectorFragment.newInstance();
        predictionsFragment = PredictionsFragment.newInstance();
        smoothedPredictionsFragment = SmoothedPredictionsFragment.newInstance();
        CameraSettingsFragment cameraSettingsFragment = new CameraSettingsFragment();
        ThreadNumberFragment threadNumberFragment = new ThreadNumberFragment();
        ProcessingUnitSelectorFragment processingUnitSelectorFragment = new ProcessingUnitSelectorFragment();


        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.add(R.id.modelselector_container, msf, "msf");

        fragmentTransaction.add(R.id.perframe_results_container, predictionsFragment, "predictionsFragment");
        fragmentTransaction.add(R.id.smoothed_results_container, smoothedPredictionsFragment, "smoothedPredictionsFragment");

        fragmentTransaction.add(R.id.camera_settings_container, cameraSettingsFragment, "cameraSettingsFragment");
        fragmentTransaction.add(R.id.thread_number_container, threadNumberFragment, "threadNumberFragment");
        fragmentTransaction.add(R.id.processing_unit_container, processingUnitSelectorFragment, "processingUnitSelectorFragment");
        fragmentTransaction.commit();


        // add ViewFinder to list of event listeners in cameraSettingsFragment, in order to get
        // notified when the user toggles the camera or flash
        cameraSettingsFragment.addListener(this);




        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +           SET UP USER INTERFACE COMPONENTS            +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init view finder that displays the camera output
        _viewFinder = findViewById(R.id.viewFinder);
        _frozenPreviewWindow = findViewById(R.id.frozen_preview);


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                  INIT CORE FEATURES                   +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // load lens rotation from SharedPreferences  [0: back, 1: front]
        lens_front_back = prefs.getInt("lens", 0);

        initCameraX();


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                SET UP EVENT LISTENERS                 +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // restart paused classification
        findViewById(R.id.btn_play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ImageView focusCircle = findViewById(R.id.focus_circle);

                // load 150ms animations
                Animation fade_in = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_in_150);
                Animation fade_out = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_out_150);

                focusCircle.startAnimation(fade_in);
                focusCircle.setVisibility(View.VISIBLE);

                findViewById(R.id.btn_play).startAnimation(fade_out);
                findViewById(R.id.btn_play).setVisibility(View.GONE);

                findViewById(R.id.view_finder_shadow).animate().alpha(0f).setDuration(150).setListener(null);

                // re-init live camera preview feed
                resetFrozenViewFinder();

                isClassificationPaused = !isClassificationPaused;
            }
        });

        // jump to 'camera roll classifier' activity
        findViewById(R.id.btn_camera_roll_activity).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                Intent intent = new Intent(ViewFinder.this, CameraRoll.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                finish();
            }
        });

    }
    // END OF 'onCreate()' METHOD
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // buildPreviewUseCase() Method
    // inits preview object that holds the camera live feed
    // [based around: https://stackoverflow.com/q/59661727]

    private void buildPreviewUseCase(){

        LOGGER.i("ViewFinder: building preview use case.");

        // init preview object that holds the camera live feed
        _preview = new Preview.Builder()
                .setTargetResolution(new Size(previewDimX, previewDimY))
                .setTargetRotation(Surface.ROTATION_0) // warum auch immer...
                .build();

        // bind and init camera feed to the corresponding object in our layout
        _preview.setSurfaceProvider(_viewFinder.createSurfaceProvider());

        // bind preview use case to CameraX lifecycle
        if(_cameraSelector != null)
            _cameraProvider.bindToLifecycle(this, _cameraSelector, _preview);

    }

    private void buildAnalyzerUseCase(){

        LOGGER.i("ViewFinder: building analysis use case.");

        // init analyzer object
        _analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(previewDimX, previewDimY))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // async
                .setTargetRotation(Surface.ROTATION_0)
                .build();

        // init new single thread ExecutorService for the analyzer use case
        _cameraExecutorForAnalysis = Executors.newSingleThreadExecutor();

        // set up the image analysis use case
        _analysis.setAnalyzer(_cameraExecutorForAnalysis, new ImageAnalysis.Analyzer() {

            @SuppressLint("UnsafeExperimentalUsageError") @Override
            public void analyze(@NonNull ImageProxy image) {

                // Logs this method so that it can be analyzed with systrace.
                Trace.beginSection("analyzing");
//                LOGGER.i("*** ViewFinder: analyze() START *** ");


                // close image if classification is halted or already running
                if(isCurrentlyClassifying || isClassificationPaused){
                    LOGGER.i("*** ViewFinder: closing as (isCurrentlyClassifying || isClassificationPaused) == true ***");
                    image.close(); // close the image in order to clear the pipeline
                    return;
                }


                // update the flag in order to block the image pipeline for the duration of the active classification
                isCurrentlyClassifying = true;


                // convert Image to Bitmap
                @androidx.camera.core.ExperimentalGetImage
                Image img = image.getImage();
                final Bitmap rgbBitmap = ImageUtils.toCroppedBitmap(img, image.getImageInfo().getRotationDegrees());


                // make sure that the bitmap is not null
                if(rgbBitmap == null){
                    LOGGER.i("*** ViewFinder: closing as Bitmap == NULL ***");
                    image.close(); // close the image in order to clear the pipeline
                    return;
                }


                final long startTime = SystemClock.uptimeMillis();

                // run inference on image
                final List<Recognition> results = NewStaticClassifier.recognizeImage(rgbBitmap);

                startTimestamp = SystemClock.uptimeMillis() - startTime;


                // pass list of results to fragments that render the recognition results to UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        predictionsFragment.showRecognitionResults(results, startTimestamp);
                        smoothedPredictionsFragment.showSmoothedRecognitionResults(results);
                    }
                });

                Trace.endSection();

                // close image no matter what to clear the pipeline
                Trace.beginSection("closing image");
                image.close();
                Trace.endSection();

                // now that the classification is done, reset the flag
                isCurrentlyClassifying = false;

//                LOGGER.i("*** ViewFinder: analyze() ENDE; received " + results.size() + "  results *** \n");

            } // END of analyze(@NonNull ImageProxy image) - - - - - - - - - - - - - - - - - - - - -
        }); // END of _analysis.setAnalyzer ...  - - - - - - - - - - - - - - - - - - - - - - - - - -

        // bind analysis use case to CameraX lifecycle
        if(_cameraSelector != null)
            _camera = _cameraProvider.bindToLifecycle(this, _cameraSelector, _analysis);

    }

    // buildFreezeUseCase()
    //
    // freeze last frame when classification is halted;
    // based on: https://stackoverflow.com/a/59674075
    //
    private void buildFreezeUseCase(){

        LOGGER.i("ViewFinder: building freeze use case.");

        // init analysis object for processing last frame
        _freezeImageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(previewDimX, previewDimY))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(Surface.ROTATION_0)
                .build();

        // init new single thread ExecutorService for the analyzer use case
        _cameraExecutorForFreezing = Executors.newSingleThreadExecutor();

        // activate
        _freezeAnalyzer = new FreezeAnalyzer(this);
        _freezeImageAnalysis.setAnalyzer(_cameraExecutorForFreezing, _freezeAnalyzer);

        // bind 'freeze' use case to CameraX lifecycle
        if(_cameraSelector != null)
            _camera = _cameraProvider.bindToLifecycle(this, _cameraSelector, _freezeImageAnalysis);

    }




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // initCameraX()
    //
    // initializes a new 'CameraX' instance, including preview and two analysis use cases
    //
    private void initCameraX() {

        LOGGER.i("ViewFinder: initializing CameraX.");

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {

            try {
                // init new camera provider
                _cameraProvider = cameraProviderFuture.get();


                // select lens by initializing a new camera selector
                if(lens_front_back == 1) { // FRONT FACING
                    _cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build();
                } else { // BACK FACING
                    _cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();
                }


                // init preview use case that holds the camera live feed
                buildPreviewUseCase();

                // init analysis use case that converts each frame to bitmap and passes it on for inference
                buildAnalyzerUseCase();

                // init use case for 'freezing' the last frame when classification is halted
                buildFreezeUseCase();


                // set up auto focus   [source: https://developer.android.com/training/camerax/configuration#java]
                // (we just want the view finder center to be in focus)
                CameraControl cameraControl = _camera.getCameraControl();
                MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(previewDimX, previewDimY);
                MeteringPoint point = factory.createPoint((int)(0.5*previewDimX), (int)(0.5*previewDimY));
                FocusMeteringAction focusMeteringAction = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(2, TimeUnit.SECONDS) //auto-focus every x seconds
                        .build();
                cameraControl.startFocusAndMetering(focusMeteringAction);


                LOGGER.i("ViewFinder: CameraX initialization complete!");

            } catch (InterruptedException | ExecutionException exception) {
                LOGGER.e("Error occurred while setting up CameraX: " + exception.toString());
            }
        }, ContextCompat.getMainExecutor(this));

    } // END of initCameraX()  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // START of TOUCH and GESTURE HANDLER section
    //
    // we use Android's GestureDetector class to detect common gestures such as swiping down

    // this function acts as a 'gateway' that passes the MotionEvent on to where it can be processed
    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (this.mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }


    // onDoubleTap   -> pauses the classification
    @Override
    public boolean onDoubleTap(MotionEvent e) {

        ImageView focusCircle = findViewById(R.id.focus_circle);

        // load 150ms animations
        Animation fade_in = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_in_150);
        Animation fade_out = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_out_150);

        if(isClassificationPaused){ // resume classification: adjust UI

            // !!! IMPORTANT !!!
            // see 'btn_pause event listener' as well!

            // re-init live camera preview feed
            resetFrozenViewFinder();

            focusCircle.startAnimation(fade_in);
            focusCircle.setVisibility(View.VISIBLE);

            findViewById(R.id.btn_play).startAnimation(fade_out);
            findViewById(R.id.btn_play).setVisibility(View.GONE);

            findViewById(R.id.view_finder_shadow).animate().alpha(0f).setDuration(150).setListener(null);

        } else { // pause classification: adjust UI


            // trigger the UI change
            _freezeAnalyzer.freeze(lens_front_back);


            focusCircle.startAnimation(fade_out);
            focusCircle.setVisibility(View.GONE);

            findViewById(R.id.btn_play).startAnimation(fade_in);
            findViewById(R.id.btn_play).setVisibility(View.VISIBLE);

            findViewById(R.id.view_finder_shadow).animate().alpha(0.5f).setDuration(150).setListener(null);
        }

        // toggle variable
        isClassificationPaused = !isClassificationPaused;

        return false;

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
    @Override
    public void onFrozenBitmap(Bitmap b) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                _frozenPreviewWindow.setImageBitmap(b);
                _frozenPreviewWindow.setVisibility(View.VISIBLE);
                _viewFinder.setVisibility(View.GONE);
            }
        });
    }

    // resetFrozenViewFinder() method
    // counterpart to the method above to restore the default
    public void resetFrozenViewFinder(){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                _frozenPreviewWindow.setVisibility(View.INVISIBLE);
                _viewFinder.setVisibility(View.VISIBLE);
            }
        });

    }
    // END - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // 'Camera Events' Interface
    @Override
    public void onRotateToggled() {

        // switch lens by changing global variable
        if(lens_front_back == 0) {
            lens_front_back = 1;
        } else {
            lens_front_back = 0;
        }

        // unbind all use cases
        _cameraProvider.unbindAll();

        // re-init CameraX
        initCameraX();

        // init Editor and save to SharedPreferences
        prefEditor = prefs.edit();
        prefEditor.putInt("lens", lens_front_back);
        prefEditor.apply();

    }

    @Override
    public void onFlashToggled() {

        // turn camera flash on or off, if there is one

        if(_camera.getCameraInfo().hasFlashUnit()){

            ImageButton btn = findViewById(R.id.btn_flash);

            // perform haptic feedback
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

            if(isFlashEnabled){
                _camera.getCameraControl().enableTorch(false);
                btn.setColorFilter(ContextCompat.getColor(ViewFinder.this, R.color.colorPrimary));
            } else {
                btn.setColorFilter(ContextCompat.getColor(ViewFinder.this, R.color.colorAccent));
                _camera.getCameraControl().enableTorch(true);
            }

            isFlashEnabled = !isFlashEnabled;
        }
    }
    // END - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // APPLICATION LIFE CYCLE

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isClassificationPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isClassificationPaused = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isClassificationPaused = true;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        isClassificationPaused = false;
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        _analysis.clearAnalyzer();
        _cameraProvider.unbindAll();

        _cameraExecutorForAnalysis.shutdownNow();
        _cameraExecutorForFreezing.shutdownNow();

    }
    // END - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // UNIMPLEMENTED GESTURE METHODS
    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) { return true; }
    @Override
    public boolean onDown(MotionEvent event) { return true; }
    @Override
    public void onLongPress(MotionEvent event) { }
    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) { return true; }
    @Override
    public void onShowPress(MotionEvent event) { }
    @Override
    public boolean onSingleTapUp(MotionEvent event) { return true; }
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {return false; }
    @Override
    public boolean onDoubleTapEvent(MotionEvent e) { return false; }
    // END - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

}