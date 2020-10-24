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
import android.app.Activity;
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

import java.io.IOException;
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
            ClassifierEvents,
            CameraEvents,
            ReinitializationListener{


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
    private Classifier classifier;

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



//    SingletonClassifier SINGLETONCLASSIFIER = SingletonClassifier.getInstance();
    NewSingletonClassifier NEWSINGLETONCLASSIFIER = NewSingletonClassifier.getInstance();




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



        // add StaticClassifier to list of event listeners
        //      please note that it is absolutely critical that this happens before all the other
        //      listeners are added to the list;
        //      otherwise the classifier will get notified too late and run the old model on the image!
        // 24.10.2020
//        StaticClassifier sc = null;
//        try {
//            sc = new StaticClassifier();
//            msf.addListener(sc);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        // 24.10.2020
//        msf.addListener(SINGLETONCLASSIFIER);
        msf.addListener(NEWSINGLETONCLASSIFIER);






        // for transmitting events back from the fragment to this class
        msf.addListener(this);
        cameraSettingsFragment.addListener(this);
        threadNumberFragment.addListener(this);
        processingUnitSelectorFragment.addListener(this);


        // 24.10.2020: onClassificationReinitialized-Event
//        SINGLETONCLASSIFIER.addListener(this);
//        SingletonClassifier.addListener(this);


        // FINDING:
        //   do NOT do this; does not work! we need to re-instantiate the whole classifier object
        // msf.addListener(classifier);




        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +           SET UP USER INTERFACE COMPONENTS            +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init view finder that displays the camera output
        _viewFinder = findViewById(R.id.viewFinder);
        _frozenPreviewWindow = findViewById(R.id.frozen_preview);


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                  INIT CORE FEATURES                   +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

//        reInitClassifier();


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
    // onClassifierConfigChanged(Activity activity)
    //
    // this method is part of the 'ClassifierEvents' Interface; it gets called when the user taps a
    // RadioButton in the 'ModelSelector' section of the BottomSheet; this method will re-init the
    // classifier object to the user's preferences by calling the constructor;
    //
    @Override
    public void onClassifierConfigChanged(Activity activity){

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +   FINDING   –   24.09.2020, 11:30
        // +
        // +    1. we need to re-init the 'Classifier' object;
        // +       just >changing< the configuration within the class based on the updated
        // +       SharedPreferences does NOT work;
        // +
        // +       we therefore call 'classifier = new Classifier(this)';
        // +       this will init a new 'Classifier' object, that is built on the current config
        // +       saved in SharedPreferences
        // +
        // +
        // +    2. before updating 'classifier', we first need to make sure, that all related
        // +       Threads are terminated (will cause SegFaults otherwise!);
        // +
        // +       for this reason, we first call '_cameraExecutorForAnalysis.shutdown();' and then
        // +       wait for the ExecutorService's termination with the while routine below
        // +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +


//        isClassificationPaused = true;
//
//
//        LOGGER.i("ViewFinder: resetting analysis use case....");
//        Trace.beginSection("ViewFinder: resetting analysis use case");
//
//
//        // first, reset and unbind
//        _analysis.clearAnalyzer();
//        _cameraProvider.unbind(_analysis);
//
//        Trace.endSection();

//         then shut down ExecutorService
//        _cameraExecutorForAnalysis.shutdown();
//
//        // wait for termination   [source: https://stackoverflow.com/a/28391316]
//        boolean isStillWaiting = true;
//        while (isStillWaiting) {
//            try {
//                isStillWaiting = !_cameraExecutorForAnalysis.awaitTermination(50, TimeUnit.MILLISECONDS);
//                if (isStillWaiting) LOGGER.d("Awaiting shutdown of '_cameraExecutorForAnalysis'.");
//            } catch (InterruptedException e) {
//                LOGGER.d("Interrupted while awaiting completion of callback threads - trying again...");
//            }
//        }
//
//        // isStillWaiting == false, so we can proceed to change the classifier config
//        LOGGER.i("'_cameraExecutorForAnalysis' is now shutdown; proceeding to re-init classifier...");
//
//        // now, close the classifier ...
////        classifier.close();
//
//        // ... and try to re-int it
////        reInitClassifier();
//
        // finally, rebuild and bind the 'Analyzer' use case

//        Trace.beginSection("ViewFinder: New analysis use case setup");
//        buildAnalyzerUseCase();
//        Trace.endSection();
//
//
//        isClassificationPaused = false;
//
//        LOGGER.i("ViewFinder: analysis use case reset complete!");

    } // END of onClassifierConfigChanged - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


//    // reInitClassifier() Method
//    // will try to instantiate a new TF-Lite classifier
//    //
//    private void reInitClassifier(){
//
//        try {
//            classifier = new Classifier(this);
//        } catch (IOException e) {
//            LOGGER.e("Error occured while trying to re-init the classifier: " + e);
//            e.printStackTrace();
//        }
//
//    }




    // 24.10.2020
    @Override
    public void onClassifierReinitialized(){
//        LOGGER.i("### ViewFinder notified about classifier reinitialization successful ### ");
//        SINGLETONCLASSIFIER = SingletonClassifier.getInstance();
//        LOGGER.i("### ViewFinder: SINGLETONCLASSIFIER = SingletonClassifier.getInstance(); ### ");
    }








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

    // TODO: analog zu oben mit 'Analyzer'
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

                LOGGER.i("*** ViewFinder: analyze() START *** ");



                // Logs this method so that it can be analyzed with systrace.
                Trace.beginSection("analyzing");


                // 24.10.2020
//                if(StaticClassifier.getIsBlocked()){
//                    LOGGER.i("*** ViewFinder: closing as Classifier is blocked! ***");
//                    image.close(); // close the image in order to clear the pipeline
//                    return;
//                }



                if(isCurrentlyClassifying || isClassificationPaused){
                    LOGGER.i("*** ViewFinder: closing as (isCurrentlyClassifying || isClassificationPaused) == true ***");
                    image.close(); // close the image in order to clear the pipeline
                    return;
                }

//                if (SINGLETONCLASSIFIER == null){
//                if (NEWSINGLETONCLASSIFIER == null){
//                    LOGGER.i("*** ViewFinder: closing as (NEWSINGLETONCLASSIFIER == null) == true ***");
//                    image.close(); // close the image in order to clear the pipeline
//                    return;
//                }



                @androidx.camera.core.ExperimentalGetImage
                Image img = image.getImage();
                final Bitmap rgbBitmap = ImageUtils.toCroppedBitmap(img, image.getImageInfo().getRotationDegrees());



//                int i = 0;
//                try {
////                    i = StaticClassifier.recognizeImage2(rgbBitmap);
//
//                    // 24.10.2020
////                    i = SINGLETONCLASSIFIER.recognizeImage2(rgbBitmap);
//                    i = NEWSINGLETONCLASSIFIER.recognizeImage(rgbBitmap);
//
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                int i = NEWSINGLETONCLASSIFIER.recognizeImage(rgbBitmap);
                LOGGER.i("Classifier output: " + i);

                Trace.endSection();





                Trace.beginSection("closing image");
                image.close();


                Trace.endSection();

                LOGGER.i("*** ViewFinder: analyze() ENDE *** \n");
//
//                // do not accept additional images if there is already a classification running
////                if(isCurrentlyClassifying || isClassificationPaused  || true){
//                if(isCurrentlyClassifying || isClassificationPaused){
//                    image.close(); // close the image in order to clear the pipeline
//                    return;
//                }
//
//                // update the flag in order to block the image pipeline for the duration of the active classification
//                isCurrentlyClassifying = true;
//
//
//                // convert the CameraX YUV ImageProxy to a (RGB?) bitmap
//                @androidx.camera.core.ExperimentalGetImage
//                Image img = image.getImage();
//                final Bitmap rgbBitmap = ImageUtils.toCroppedBitmap(img, image.getImageInfo().getRotationDegrees());
//
//
//                // pre classification checks
////                if (classifier == null) {
////                    image.close();
////                    return;
////                }
////
////
//                if (rgbBitmap == null) {
//                    LOGGER.i("ViewFinder: closing due to 'rgbBitmap == null'!");
//                    image.close();
//                    return;
//                }
//
//                final long startTime = SystemClock.uptimeMillis();
//
//
//                // TODO:   RECONSIDER
//                // this part was initially wrapped within 'new Thread( new Runnable() { @Override public void run() { }}).start();'
//                //
//                // -> seemed kind of unnecessary, as the 'Analyzer' itself is running within a single
//                //    threaded ExecutorService; so I removed it for now;
//
//                // run inference on image
////                final List<Classifier.Recognition> results = classifier.recognizeImage(rgbBitmap);
////                final List<StaticClassifier.Recognition> results;
//                List<StaticClassifier.Recognition> results = null;
//                try {
//                    results = StaticClassifier.recognizeImage(rgbBitmap);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                startTimestamp = SystemClock.uptimeMillis() - startTime;
//
//
//                // Android Studio Workaround....
//                final List<StaticClassifier.Recognition> finalResults = results;
//
////                runOnUiThread(new Runnable() {
////                    @Override
////                    public void run() {
////                        // pass list to fragment, that renders the recognition results to UI
////                        predictionsFragment.showRecognitionResults(finalResults, startTimestamp);
//////                        smoothedPredictionsFragment.showSmoothedRecognitionResults(results);
////                    }
////                });
//
//
//                // now that the classification is done, reset the flag
//                isCurrentlyClassifying = false;
//
//                // close the image no matter what
//                image.close();

            } // END of analyze(@NonNull ImageProxy image) - - - - - - - - - - - - - - - - - - - - -
        }); // END of _analysis.setAnalyzer ...  - - - - - - - - - - - - - - - - - - - - - - - - - -

        // bind analysis use case to CameraX lifecycle
        if(_cameraSelector != null)
            _camera = _cameraProvider.bindToLifecycle(this, _cameraSelector, _analysis);

    }

    // TODO: analog zu oben
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

//        isClassificationPaused = true;
//        classifier.close();
//
//        _analysis.clearAnalyzer();
//        _cameraProvider.unbindAll();
//
//        _cameraExecutorForAnalysis.shutdownNow();
//        _cameraExecutorForFreezing.shutdownNow();

        super.onDestroy();
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