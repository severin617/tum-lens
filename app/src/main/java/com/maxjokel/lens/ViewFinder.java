package com.maxjokel.lens;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
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
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
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
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import helpers.Logger;

// TF-Lite imports
import tflite.Classifier;
import tflite.Classifier.Device;
import tflite.Classifier.Model;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    08.07.2020, 15:35

    This is the main activity of the app. It contains a view finder that displays the cameras' live
    feed.
    ...
    ...

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class ViewFinder extends AppCompatActivity
        implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener,
                   FreezeCallback {
// unfortunately we currently need both, the OnGestureListener and OnDoubleTapListener, in order
// to make the DoubleTap-Listener work...


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // init new Logger instance
    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // CameraX related:   [source: https://developer.android.com/training/camerax/preview#java]
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture = null;

    private ProcessCameraProvider _cameraProvider = null;

    private CameraSelector _cameraSelector = null;

    private Camera _camera = null;

    private Preview       _preview  = null; // CameraX use cases for live preview feed
    private ImageAnalysis _analysis = null; // CameraX use cases for actual classification

    private ExecutorService _cameraExecutorForAnalysis = null;

    private Boolean isFlashEnabled = false;

    // Layout element
    private PreviewView _viewFinder;
    private ImageView _frozenPreviewWindow;


    private CustomAnalyzer _freezeAnalyzer = null;
    private ImageAnalysis _freezeImageAnalysis = null;


    int lens_front_back = 0; // [0 = back, 1 = front]


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // TF-Lite related to IMAGE UTILS:   [source: TF-Lite example app]
    protected byte[][] yuvBytes = new byte[3][];
    protected int[] rgbBytes = null;
    protected int yRowStride;
    private Runnable imageConverterRunnable;


    // TF-Lite related to CLASSIFICATION:   [source: TF-Lite example app]
    private Classifier classifier;

    protected int previewDimX = 960; //480;
    protected int previewDimY = 1280; //640;

    // TAKEAWAY: all "Google Models" use 224x224 images as input layer
    // TODO: streamlining
    protected int modelInputX = 224;
    protected int modelInputY = 224;

    private long startTimestamp;

    private boolean isCurrentlyClassifying = false;
    private boolean isClassificationPaused = false;


    protected Model _model = Model.FLOAT_MOBILENET;
    protected int _numberOfThreads = 3; // default is 3
    protected Device _processingUnit = Device.CPU; // CPU is default

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // 'double tap' gesture
    private GestureDetectorCompat mGestureDetector;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // initialize bottom sheet for classification results
    private NestedScrollView _bottomSheet;
    private BottomSheetBehavior bottomSheetBehavior;




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private ImageButton _playButton;
    private ImageView _focusCircle;
    private LinearLayout _viewFinderShaddow;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private int _counter = 0;
    private List<ResultItem> _list = null;

    //    Map<Integer, ResultItem> _map = new HashMap<Integer, ResultItem>();
    Map<String, ResultItem> _map = new HashMap<String, ResultItem>(); // hashCode() eignet sich nicht, müssen doch getId() verwenden! -> String

    List<Classifier.Recognition> _sammlung = new LinkedList<Classifier.Recognition>();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // instantiate new SharedPreferences object
    SharedPreferences prefs = null;

    SharedPreferences.Editor prefEditor = null;

    // TODO: das muss vermutlich in das onCreate() rein





    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        // load sharedPreferences object and set up editor
        prefs = getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);
        prefEditor = prefs.edit();


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
    // +           SET UP USER INTERFACE COMPONENTS            +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init view finder that displays the camera output
        _viewFinder = findViewById(R.id.viewFinder);

        _frozenPreviewWindow = findViewById(R.id.frozen_preview);


        // TODO: wenn man die Objekte in der 'onDoubleTap' einfach neu läd, dann kann man die auch hier lokal setzen
        // in center of view finder to visualize auto focus area
        _focusCircle = findViewById(R.id.focus_circle);


        // restart the classification if paused
        _playButton = findViewById(R.id.btn_play);

        // dims down the view finder when classification is paused
        _viewFinderShaddow = findViewById(R.id.view_finder_shadow);


        // initialize bottom sheet for classification results and settings
        // TODO: local
        // TODO: braucht's das überhaupt?
        _bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(_bottomSheet);
//        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
//            @Override
//            public void onStateChanged(@NonNull View bottomSheet, int newState) {}
//            @Override
//            public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
//        });











    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +   LOAD SHARED PREFERENCES and init app accordingly    +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // number of threads
        int saved_numberOfThreads = prefs.getInt("threads", 0);
        if ((saved_numberOfThreads > 0) && (saved_numberOfThreads <= 15)) {
            _numberOfThreads = saved_numberOfThreads; // update if within accepted range
        }


        // device
        int saved_device = prefs.getInt("device", 0);

        if(saved_device == Device.GPU.hashCode()){
            _processingUnit = Device.GPU;
        } else if(saved_device == Device.NNAPI.hashCode()){
            _processingUnit = Device.NNAPI;
        } else { // use CPU as default
            _processingUnit = Device.CPU;
        }

        String cName = "chip_" + _processingUnit.name();
        int cId = getResources().getIdentifier(cName, "id", getPackageName());
        Chip c = findViewById(cId);
        c.setChecked(true);


        // model
        int helper = 0;
        int saved_model = prefs.getInt("model", 0);

        if(saved_model == Model.QUANTIZED_MOBILENET.hashCode()){
            _model = Model.QUANTIZED_MOBILENET;
            helper = 2;
        } else if (saved_model == Model.FLOAT_EFFICIENTNET.hashCode()) {
            _model = Model.FLOAT_EFFICIENTNET;
            helper = 3;
        } else if (saved_model == Model.QUANTIZED_EFFICIENTNET.hashCode()) {
            _model = Model.QUANTIZED_EFFICIENTNET;
            helper = 4;
        } else if (saved_model == Model.INCEPTION_V1.hashCode()) {
            _model = Model.INCEPTION_V1;
            helper = 5;
        } else if (saved_model == Model.INCEPTION_V1_selfConverted.hashCode()) {
            _model = Model.INCEPTION_V1_selfConverted;
            helper = 6;
        } else { // set FLOAT_MOBILENET as default
            _model = Model.FLOAT_MOBILENET;
            helper = 1;
        }

        // set initial RadioButton selection
        String rName = "radioButton" + helper;
        int rId = getResources().getIdentifier(rName, "id", getPackageName());
        RadioButton r = findViewById(rId);
        r.setChecked(true);


        // lens   [0: back, 1: front]
        lens_front_back = prefs.getInt("lens", 0);


    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +


    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +                  INIT CORE FEATURES                   +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        updateThreadCounter();

        reInitClassifier();

        // initialize CameraX
        initCameraX();






    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +                SET UP EVENT LISTENERS                 +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // restart paused classification
        _playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // load 150ms animations
                Animation fade_in = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_in_150);
                Animation fade_out = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_out_150);

                _focusCircle.startAnimation(fade_in);
                _focusCircle.setVisibility(View.VISIBLE);

                _playButton.startAnimation(fade_out);
                _playButton.setVisibility(View.GONE);

                _viewFinderShaddow.animate().alpha(0f).setDuration(150).setListener(null);

                // re-init live camera preview feed
                resetFrozenViewFinder();

                isClassificationPaused = !isClassificationPaused;
            }
        });

        // rotate camera
        ImageButton btn_rotate = findViewById(R.id.btn_rotate);
        btn_rotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // perform haptic feedback
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

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

                prefEditor.putInt("lens", lens_front_back);
                prefEditor.apply();

            }
        });

        // turn flash on or off
        ImageButton btn_flash = findViewById(R.id.btn_flash);
        btn_flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(_camera.getCameraInfo().hasFlashUnit()){

                    // perform haptic feedback
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                    if(isFlashEnabled){
                        _camera.getCameraControl().enableTorch(false);
                        btn_flash.setColorFilter(ContextCompat.getColor(ViewFinder.this, R.color.colorPrimary));
                    } else {
                        btn_flash.setColorFilter(ContextCompat.getColor(ViewFinder.this, R.color.colorAccent));
                        _camera.getCameraControl().enableTorch(true);
                    }

                    isFlashEnabled = !isFlashEnabled;

                }
            }
        });


        // jump to validator view
        ImageButton btn_validate = findViewById(R.id.btn_validate);
        btn_validate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                Intent intent = new Intent(ViewFinder.this, ImageValidator.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                finish();
            }
        });

        // jump to camera roll classifier view
        findViewById(R.id.btn_camera_roll_activity).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                Intent intent = new Intent(ViewFinder.this, CameraRollClassifier.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                finish();
            }
        });




        // decrease or increase number of threads
        ImageButton btn_threads_minus = findViewById(R.id.btn_threads_minus);
        btn_threads_minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                if(_numberOfThreads > 1){ _numberOfThreads--; }
                updateThreadCounter();
                reInitClassifier();
            }
        });
        ImageButton btn_threads_plus = findViewById(R.id.btn_threads_plus);
        btn_threads_plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                if(_numberOfThreads < 15){ _numberOfThreads++; }
                updateThreadCounter();
                reInitClassifier();
            }
        });



    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +           NETWORK SELECTOR via RadioGroup             +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init RadioGroup and event listener
        RadioGroup radioGroup = findViewById(R.id.modelSelector_RadioGroup);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                // perform haptic feedback
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                switch (checkedId) {
                    case R.id.radioButton1:
                        _model = Model.FLOAT_MOBILENET;
                        break;
                    case R.id.radioButton2:
                        _model = Model.QUANTIZED_MOBILENET;
                        break;
                    case R.id.radioButton3:
                        _model = Model.FLOAT_EFFICIENTNET;
                        break;
                    case R.id.radioButton4:
                        _model = Model.QUANTIZED_EFFICIENTNET;
                        break;
                    case R.id.radioButton5:
                        _model = Model.INCEPTION_V1;
                        break;
                    case R.id.radioButton6:
                        _model = Model.INCEPTION_V1_selfConverted;
                        break;
                    default:
                        _model = null;
                        break;
                }

                // save selection to SharedPreferences
                prefEditor.putInt("model", _model.hashCode());
                prefEditor.apply();

                // update classifier
                reInitClassifier();
            }
        });



    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +        PROCESSING UNIT SELECTOR via ChipGroup         +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init chip group and event listener
        ChipGroup chipGroup = findViewById(R.id.chipGroup_processing_unit);
        chipGroup.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {

                // perform haptic feedback
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                switch (checkedId) {
                    case R.id.chip_CPU:
                        _processingUnit = Device.CPU;
                        break;
                    case R.id.chip_GPU:
                        _processingUnit = Device.GPU;
                        break;
                    case R.id.chip_NNAPI:
                        _processingUnit = Device.NNAPI;
                        break;
                    default:
                        _processingUnit = null;
                        break;
                }

                // save selection to SharedPreferences
                prefEditor.putInt("device", _processingUnit.hashCode());
                prefEditor.apply();

                // update classifier
                reInitClassifier();

            }
        });
    }
    // END OF 'onCreate()' METHOD
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // reInitClassifier() Method
    // will try to instantiate a new TF-Lite classifier
    //
    protected void reInitClassifier(){
        try {
            LOGGER.d("Creating classifier (model=%s, device=%s, numThreads=%d)", _model, _processingUnit, _numberOfThreads);

            classifier = Classifier.create(this, _model, _processingUnit, _numberOfThreads);

        } catch (IOException e) {
            LOGGER.e(e, "Failed to create classifier.");
        }
    }






    // ----------------------------
    // LIFE CYCLE
    //  -> https://michaelkipp.de/android/lifecycle.html
    // ----------------------------

    // für den "Wiedereintritt" in die Activity
    //   - z.B. Instanzvariablen aktualisieren
    @Override
    protected void onResume() {

        super.onResume();
        isClassificationPaused = false;

    }


    // wenn die App in den "Schwebezustand" versetzt wird, also den
    // FOKUS verliert, aber immer noch läuft
    @Override
    protected void onPause() {

        super.onPause();
        isClassificationPaused = true;
    }

    @Override
    protected void onStop() {
        super.onStop();

        // TODO
        // z.B: die Klassifizierung beenden
        // z.B: CameraX beenden
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // hier die Klassifizierung und CameraX wieder neu instanzieren
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO: analog zu onStop()
    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
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

        // load 150ms animations
        Animation fade_in = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_in_150);
        Animation fade_out = (Animation) AnimationUtils.loadAnimation(ViewFinder.this, R.anim.basic_fade_out_150);

        if(isClassificationPaused){ // resume classification: adjust UI

            // !!! IMPORTANT !!!
            // see 'btn_pause event listener' as well!

            // re-init live camera preview feed
            resetFrozenViewFinder();

            _focusCircle.startAnimation(fade_in);
            _focusCircle.setVisibility(View.VISIBLE);

            _playButton.startAnimation(fade_out);
            _playButton.setVisibility(View.GONE);

            _viewFinderShaddow.animate().alpha(0f).setDuration(150).setListener(null);

        } else { // pause classification: adjust UI


            // trigger the UI change
            _freezeAnalyzer.freeze(lens_front_back);


            _focusCircle.startAnimation(fade_out);
            _focusCircle.setVisibility(View.GONE);

            _playButton.startAnimation(fade_in);
            _playButton.setVisibility(View.VISIBLE);

            _viewFinderShaddow.animate().alpha(0.5f).setDuration(150).setListener(null);
        }

        // toggle variable
        isClassificationPaused = !isClassificationPaused;

        return false;

    }





    // END
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // buildPreviewUseCase() Method
    //
    // inits preview object that holds the camera live feed
    //
    // IDEA: freeze camera-preview when classification is halted
    // TODO

    private void buildPreviewUseCase(){

        // concept based around:
        // https://stackoverflow.com/questions/59661727/how-to-make-camerax-preview-freeze-when-take-a-photo

        // init preview object that holds the camera live feed
        _preview = new Preview.Builder()
                .setTargetResolution(new Size(previewDimX, previewDimY))
                .setTargetRotation(Surface.ROTATION_0) // warum auch immer...
                .build();


        // bind and init camera feed to the corresponding object in our layout
        _preview.setSurfaceProvider(_viewFinder.createSurfaceProvider());

        // bind preview to CameraX lifecycle
        _cameraProvider.bindToLifecycle(this, _cameraSelector, _preview);



// TODO: das wurde mit der Alpha V0.7 entfernt :/
// siehe auch hier: https://github.com/google/mediapipe/issues/472
// IDEE von hier: https://stackoverflow.com/a/59045412
//        preview.setOnPreviewOutputUpdateListener {
//            previewOutput: Preview.PreviewOutput? ->
//            if(!frozen)
//                textureView.setSurfaceTexture(previewOutput.getSurfaceTexture());
//        }


    }



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // initCameraX() Method
    // initializes CameraX and with that the live preview and image analyzer
    //
    // lens_front_back
    //  -> 0: lens facing back
    //  -> 1: lens facing front
    //

    private void initCameraX() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {

                // init new camera provider
                 _cameraProvider = cameraProviderFuture.get();


                // select lens (front or back) ...
                // by initializing a new camera selector
                if(lens_front_back == 1) { // FRONT FACING
                    _cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build();
                } else { // BACK FACING
                    _cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();
                }


                // init preview object that holds the camera live feed


                buildPreviewUseCase();
//                _preview = new Preview.Builder()
//                        .setTargetResolution(new Size(previewDimX, previewDimY)) // damit ist alles DEUTLICH SCHNELLER!!
////                        .setTargetRotation(Surface.ROTATION_180) // warum auch immer...
//                        .setTargetRotation(Surface.ROTATION_0) // warum auch immer...
//                        .build();


                // init analysis object that converts every frame to a RGB bitmap and gives it to the classifier
                _analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(previewDimX, previewDimY)) // 06.06.2020, 18 Uhr: Bitmap muss QUADRATISCH sein
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // async
//                        .setTargetRotation(Surface.ROTATION_180) // warum auch immer...
                        .setTargetRotation(Surface.ROTATION_0) // warum auch immer...
                        .build();

                // set up the image analysis use case
                _cameraExecutorForAnalysis = Executors.newSingleThreadExecutor();
                _analysis.setAnalyzer(_cameraExecutorForAnalysis, new ImageAnalysis.Analyzer() {

                    // - - - - - - - - - - - - - - - - - - - - - - - - - -
                    @SuppressLint("UnsafeExperimentalUsageError")
                    @Override
                    public void analyze(@NonNull ImageProxy image) {

                        // do not accept additional images if there is already a classification running
                        if(isCurrentlyClassifying || isClassificationPaused){
                            image.close(); // close the image in order to clear the pipeline - IMPORTANT - IMPORTANT - IMPORTANT
                            return;
                        }


                        // update the flag in order to block the image pipeline for the duration of the active classification
                        isCurrentlyClassifying = true;


//                        System.out.println("Rotation (in degrees): " + image.getImageInfo().getRotationDegrees());


                        // convert the CameraX YUV ImageProxy to a (RGB?) bitmap
                        @androidx.camera.core.ExperimentalGetImage
                        Image img = image.getImage();

                        // TODO: ERKENNTNIS 17.06, 18:51
                        //       das mit der globalen Bitmap macht NUR Probleme -> NullPointerException;
                        //       lokal erscheint die bessere Wahl zu sein
//                        final Bitmap rgbBitmap = toBitmap(img);
                        final Bitmap rgbBitmap = toCroppedRGBBitmap(img);

//                        final Bitmap rgbBitmap = Bitmap.createBitmap(toBitmap(img), 0, 0, previewDimX, previewDimY);


                        // -------------
//                        System.out.println("-> image  Width and Height: " + image.getWidth() + "px x " + image.getHeight() + "px");
//                        System.out.println("-> bitmap Width and Height: " + rgbBitmap.getWidth() + "px x " + rgbBitmap.getHeight() + "px");
                        // IMAGE:  352px x 288px = 101.376px
                        // BITMAP: 224px x 224px = 50.176px

//                        isCurrentlyClassifying = false;
//                        classifier = null;
                        // -------------

                        // pre classification checks
                        if (classifier == null) {
                            image.close();
                            return;
                        }


                        // TODO: muss der Thread eig wieder beendet werden?

                        // TODO: ist ASYNC hier nicht eher hinderlich?!
                        //       hat zwar initial ein Problem gelöst, aber ich bin mir echt nicht sicher
                        //       17.06, 18:32 Uhr


                        // and pass the frame to the classifier that is started in a new thread
                        AsyncTask.execute(new Runnable() {   // [source: https://stackoverflow.com/a/31549559]
                            @Override
                            public void run() {
                                final long startTime = SystemClock.uptimeMillis();
                                final List<Classifier.Recognition> results = classifier.recognizeImage(rgbBitmap, 0);
                                startTimestamp = SystemClock.uptimeMillis() - startTime;

                                runOnUiThread( new Runnable() {
                                    @Override
                                    public void run() {
                                        showResultsInBottomSheet(results);

                                        showSmoothedResults(results);

                                        final TextView time = findViewById(R.id.time);
//                                        time.setText("this classification took " + startTimestamp + "ms");
                                        time.setText("classifying this frame took " + startTimestamp + "ms");
//                                        System.out.println("+YES at" + SystemClock.uptimeMillis() + " und this classification took " + startTimestamp + "ms");
                                    }
                                });

                                // now that the classification is done, reset the flag
                                isCurrentlyClassifying = false;
                            }
                        });

                        // TODO: nochmal überdenken, ob das nicht doch auch zum Flag dazu muss
                        // close the image no matter what
                        image.close();


                    }
                    // end of method
                    // - - - - - - - - - - - - - - - - - - - - - - - - - -
                });







//                // TODO: freeze preview on halt
//                // based on: https://stackoverflow.com/a/59674075
                ExecutorService freezeExecutor = Executors.newSingleThreadExecutor();

                _freezeImageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(previewDimX, previewDimY))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // async
                        .setTargetRotation(Surface.ROTATION_0)
                        .build();

//                CustomAnalyzer freezeAnalyzer = new CustomAnalyzer();
                _freezeAnalyzer = new CustomAnalyzer(this);
                _freezeImageAnalysis.setAnalyzer(freezeExecutor, _freezeAnalyzer);

                // WICHTIG: muss natürlich auch an den LifeCycle gebunden werden!!!
                _camera = _cameraProvider.bindToLifecycle(this, _cameraSelector, _freezeImageAnalysis);




                // Unbind any prior use cases before rebinding the ones we just set up
                // must be removed, breaks the 'freeze preview on pause'
//                _cameraProvider.unbindAll();

                // Bind use cases to camera: {Preview, Analysis}
                // TODO
//                _camera = cameraProvider.bindToLifecycle(this, cameraSelector, _preview, _analysis);
                _camera = _cameraProvider.bindToLifecycle(this, _cameraSelector, _analysis);


                // bind and init camera feed to the corresponding object in our layout
//                PreviewView viewFinder = findViewById(R.id.viewFinder); no longer needed
//                _preview.setSurfaceProvider(_viewFinder.createSurfaceProvider()); // def camerax_version = "1.0.0-beta04"
                //_preview.setSurfaceProvider(viewFinder.createSurfaceProvider(_camera.getCameraInfo())); // def camerax_version = "1.0.0-beta03"

                // AUTO FOCUS   [Source: https://developer.android.com/training/camerax/configuration#java]
                CameraControl cameraControl = _camera.getCameraControl();

                MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(previewDimX, previewDimY);
                MeteringPoint point = factory.createPoint((int)(0.5*previewDimX), (int)(0.5*previewDimY));

                FocusMeteringAction focusMeteringAction = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(2, TimeUnit.SECONDS) //auto-focus every x seconds
                        .build();

                // we just need the 'auto focus' itself: the center of the preview-window should be in focus
                // for that reason, we do not need any focus related Future
                // ListenableFuture<FocusMeteringResult> focusMeteringFuture = cameraControl.startFocusAndMetering(focusMeteringAction);
                cameraControl.startFocusAndMetering(focusMeteringAction);


            } catch (InterruptedException | ExecutionException exception) {
                Log.println(Log.ERROR, "", "Es ist ein Fehler aufgetreten");
                System.out.println("\n ### ### ###   FEHLER   ### ### ###");
                System.out.println(exception.toString());
                System.out.println("### ### ###  ENDE FEHLER  ### ### ###\n");
                // No errors need to be handled for this Future. This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));

    }
    // end of method
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -





    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // toBitmap() Method
    //
    // >>based on the method developed in ImageValidator.java<<
    //
    // Workflow [currently only validated with square dimensions]
    //  - convert YUV image to RGB bitmap:
    //      - reduce quality to 35%
    //      - crop to square (length is determined by shortest side of image)
    //  - scale square down to model input requirements
    //
    // Takeaways
    //  - "quality" doesn't seem to have a huge effect on conversion time
    //  - both, 25% and 50% run in sub-20ms time frames
    //
    private Bitmap toCroppedRGBBitmap(Image image) {

//        System.out.println("before convert + crop: " + image.getWidth() + "px x " + image.getHeight() + "px");

        // STEP 1: convert YUV image to RGB bitmap and crop   [source: https://stackoverflow.com/a/58568495]
        Image.Plane[] planes = image.getPlanes();

        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // IDEA: the object of desire is probably in the image center;
        // so it should not matter, if we loose some pixels near the bezels
        // -> factor is currently 90% of the smaller side
        int cropSize = (int)(0.9 * Math.min(image.getWidth(), image.getHeight()));

        // calc offsets for cropping
        int offShortSide = (int)(0.5 * (Math.min(image.getWidth(), image.getHeight()) - cropSize));
        int offLongSide = (int)(0.5 * (Math.max(image.getWidth(), image.getHeight()) - cropSize));

        // convert to RGB and crop; quality is set to 35%
        if (image.getWidth() < image.getHeight()){
            // PORTRAIT
            yuvImage.compressToJpeg(
                    new Rect(
                            offShortSide,                       // left
                            offLongSide,                        // top
                            (image.getWidth()-offShortSide),    // right
                            (image.getHeight()-offLongSide)),   // bottom
                    35, out);                            // note the byte-buffer at the end of the command
        } else {
            // LANDSCAPE
            yuvImage.compressToJpeg(
                    new Rect(
                            offLongSide,
                            offShortSide,
                            (image.getWidth()-offLongSide),
                            (image.getHeight()-offShortSide)),
                    35, out);
        }

        // finally, create bitmap
        byte[] imageBytes = out.toByteArray();
        Bitmap temp1 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);


//        System.out.println("after convert + crop: " + temp1.getWidth() + "px x " + temp1.getHeight() + "px");


        // STEP 2: scale down to required dimensions
        Bitmap temp2 = Bitmap.createScaledBitmap(temp1, modelInputX, modelInputY, true);

        // STEP 3: rotate by 90 degrees
        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        return Bitmap.createBitmap(temp2, 0, 0, temp2.getWidth(), temp2.getHeight(), matrix, true);

    }
    // end of method - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // updateThreadCounter()
    //
    //   - updates component in BottomSheet accordingly
    //   - saves number of threads to SharedPreferences object

    protected void updateThreadCounter(){

        // save number of threads to sharedPreferences
        prefEditor.putInt("threads", _numberOfThreads);
        prefEditor.apply();

        // update UI
        TextView tv = findViewById(R.id.tv_threads);
        tv.setText( "" + _numberOfThreads);
    }
    // end of method
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // showResultsInBottomSheet() Method
    //
    // runs in UI Thread
    // displays the classification statistics to the user

    @UiThread
    protected void showResultsInBottomSheet(List<Classifier.Recognition> results) {

        // PLEASE NOTE
        // Number of results to show in the UI is defined as follows in 'Classifier.java'
        // private static final int MAX_RESULTS = 5;

        final TextView desc1 = findViewById(R.id.description1);
        final TextView conf1 = findViewById(R.id.confidence1);

        final TextView desc2 = findViewById(R.id.description2);
        final TextView conf2 = findViewById(R.id.confidence2);

        final TextView desc3 = findViewById(R.id.description3);
        final TextView conf3 = findViewById(R.id.confidence3);

        final TextView desc4 = findViewById(R.id.description4);
        final TextView conf4 = findViewById(R.id.confidence4);

        final TextView desc5 = findViewById(R.id.description5);
        final TextView conf5 = findViewById(R.id.confidence5);

        if (results != null) {

            // result at index 0
            Classifier.Recognition recognition0 = results.get(0);
            if (recognition0 != null) {
                if (recognition0.getTitle() != null) desc1.setText(recognition0.getTitle());
                if (recognition0.getConfidence() != null) conf1.setText(String.format("%.1f", (100 * recognition0.getConfidence())) + "%");
            }

            // result at index 1
            Classifier.Recognition recognition1 = results.get(1);
            if (recognition1 != null) {
                if (recognition1.getTitle() != null) desc2.setText(recognition1.getTitle());
                if (recognition1.getConfidence() != null) conf2.setText(String.format("%.1f", (100 * recognition1.getConfidence())) + "%");
            }

            // result at index 2
            Classifier.Recognition recognition3 = results.get(2);
            if (recognition3 != null) {
                if (recognition3.getTitle() != null) desc3.setText(recognition3.getTitle());
                if (recognition3.getConfidence() != null) conf3.setText(String.format("%.1f", (100 * recognition3.getConfidence())) + "%");
            }

            // result at index 3
            Classifier.Recognition recognition4 = results.get(3);
            if (recognition4 != null) {
                if (recognition4.getTitle() != null) desc4.setText(recognition4.getTitle());
                if (recognition4.getConfidence() != null) conf4.setText(String.format("%.1f", (100 * recognition4.getConfidence())) + "%");
            }

            // result at index 4
            Classifier.Recognition recognition5 = results.get(4);
            if (recognition5 != null) {
                if (recognition5.getTitle() != null) desc5.setText(recognition5.getTitle());
                if (recognition5.getConfidence() != null) conf5.setText(String.format("%.1f", (100 * recognition5.getConfidence())) + "%");
            }

        } else {
            LOGGER.v("The result list is empty!");
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // showSmoothedResults() Method
    //
    // Background:
    //   the classification is executed on a 'per frame' basis
    //   this makes it hard to read the actual classification results
    //
    // Idea:
    //   introduce 'artificial latency' into the process of displaying the classification results
    //   by taking an average over the last x classification results

    private void showSmoothedResults(List<Classifier.Recognition> results) {

        if (_counter < 9){

            // Idea:
            // while the counter is less than 9, add the first three most promising classification results to a list

            if (results != null) { // if there are results, add them to the list

                Classifier.Recognition recognition0 = results.get(0);
                if ((recognition0 != null) && (recognition0.getTitle() != null) && (recognition0.getConfidence() != null))  {
                    _sammlung.add(recognition0);
                }
                Classifier.Recognition recognition1 = results.get(1);
                if ((recognition1 != null) && (recognition1.getTitle() != null) && (recognition1.getConfidence() != null))  {
                    _sammlung.add(recognition1);
                }
                Classifier.Recognition recognition2 = results.get(2);
                if ((recognition2 != null) && (recognition2.getTitle() != null) && (recognition2.getConfidence() != null))  {
                    _sammlung.add(recognition2);
                }
                Classifier.Recognition recognition3 = results.get(3);
                if ((recognition3 != null) && (recognition3.getTitle() != null) && (recognition3.getConfidence() != null))  {
                    _sammlung.add(recognition3);
                }
                Classifier.Recognition recognition4 = results.get(4);
                if ((recognition4 != null) && (recognition4.getTitle() != null) && (recognition4.getConfidence() != null))  {
                    _sammlung.add(recognition4);
                }

                // increment counter
                _counter++;

            }

        } else { // now, the list should contain ~ 10x5 = 50 elements

            // IDEA
            //  - create a new 'List' data structure, that holds the custom 'ResultItem' objects
            //  - iterate over the last 10 classified frames and group multiple occurring results
            //     - if the new list is empty: just add the element
            //     - else try to find the previously added instance and update it
            //     - else add as a new element

            // new 'List' data structure
            List<ResultItem> list = new LinkedList<ResultItem>();

            // iterate
            for (int i=0; i < _sammlung.size(); i++) {

                Classifier.Recognition a = _sammlung.get(i); // improve memory efficiency

                if (list.size() == 0){ // if list is empty, just add ResultItem to it

                    ResultItem c = new ResultItem(a.getTitle(), a.getConfidence());
                    list.add(c);

                } else { // else, look for previous occurrences

                    Boolean foundIt = false;

                    for (int j=0; j < list.size(); j++) {
                        if(list.get(j).getTitle() == a.getTitle()){ // is already element of list

                            // load element from List
                            ResultItem b = list.get(j);

                            // update element
                            b.incOccurrences();
                            b.addToConfidence(a.getConfidence());

                            // put back into List
                            list.set(j, b);

                            foundIt = true;
                        }
                    }

                    if(!foundIt){ // otherwise, just add it to the list
                        ResultItem c = new ResultItem(a.getTitle(), a.getConfidence());
                        list.add(c);
                    }

                }
            }

//            // print out list
//            for (int i=0; i < list.size(); i++) { System.out.println("i: " + i + "   " + list.get(i).getTitle() + " " + list.get(i).getOccurrences()); }

            // STEP 2: sort by number of occurrences and confidence level
            Collections.sort(list, new ResultItemComparator());


            // STEP 3: output the first 5 list elements
            if(list.size() >= 5){

                final LinearLayout placeholder = findViewById(R.id.placeholder);
                final LinearLayout actualrslts = findViewById(R.id.actual_result);
                placeholder.setVisibility(View.GONE);
                actualrslts.setVisibility(View.VISIBLE);

                ResultItem r1 = (ResultItem) list.get(0);
                final TextView desc1 = findViewById(R.id.pf_description1);
                final TextView conf1 = findViewById(R.id.pf_confidence1);
                desc1.setText(r1.getTitle() + " (" + r1.getOccurrences() + "x)");
                conf1.setText(String.format("%.1f", (100 * r1.getConfidence() / r1.getOccurrences())) + "%");


                ResultItem r2 = (ResultItem) list.get(1);
                final TextView desc2 = findViewById(R.id.pf_description2);
                final TextView conf2 = findViewById(R.id.pf_confidence2);
                desc2.setText(r2.getTitle() + " (" + r2.getOccurrences() + "x)");
                conf2.setText(String.format("%.1f", (100 * r2.getConfidence() / r2.getOccurrences())) + "%");

                ResultItem r3 = (ResultItem) list.get(2);
                final TextView desc3 = findViewById(R.id.pf_description3);
                final TextView conf3 = findViewById(R.id.pf_confidence3);
                desc3.setText(r3.getTitle() + " (" + r3.getOccurrences() + "x)");
                conf3.setText(String.format("%.1f", (100 * r3.getConfidence() / r3.getOccurrences())) + "%");

                ResultItem r4 = (ResultItem) list.get(3);
                final TextView desc4 = findViewById(R.id.pf_description4);
                final TextView conf4 = findViewById(R.id.pf_confidence4);
                desc4.setText(r4.getTitle() + " (" + r4.getOccurrences() + "x)");
                conf4.setText(String.format("%.1f", (100 * r4.getConfidence() / r4.getOccurrences())) + "%");

                ResultItem r5 = (ResultItem) list.get(4);
                final TextView desc5 = findViewById(R.id.pf_description5);
                final TextView conf5 = findViewById(R.id.pf_confidence5);
                desc5.setText(r5.getTitle() + " (" + r5.getOccurrences() + "x)");
                conf5.setText(String.format("%.1f", (100 * r5.getConfidence() / r5.getOccurrences())) + "%");

            }


            // reset data structures for next iteration
            _counter = 0;
            _map = new HashMap<String, ResultItem>();
            _sammlung = new LinkedList<Classifier.Recognition>();

        }

    }

    // end of method
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -






    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // unimplemented gesture-related stuff


    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }




    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
        return true;
    }
////
////        if (_isSettingsOverlayDisabled) return false;
////
////        // BASIC IDEA
////        //
////        // we use 'swipe down' and 'swipe up' gestures to show and hide the settings panel.
////        //
////        // when a so called 'fling' gesture is detected, we verify its distance and velocity to make
////        // sure, that the gesture was intended by the user.
////        // we currently have no use for 'swipe left' or 'swipe right', so we discard those events.
////        //
////        // when detecting 'swipe down' and 'swipe up' gestures, we are only interested in the vertical axis
////        //
////        // the actual animation is defined as xml, see /anim directory
////        //
////        // somewhat useful reference:
////        //   https://androidexample.com/Swipe_screen_left__right__top_bottom/index.php?view=article_discription&aid=95&aaid=118
////
//////        Log.d(DEBUG_TAG, "onFling: " + event1.toString() + event2.toString());
//////        Log.d(DEBUG_TAG, "onFling!  X: " + event1.getX() + " to " + event2.getX() + "  and  Y: " + event1.getY() + " to " + event2.getY());
//////        Log.d(DEBUG_TAG, "onFling!  Y: " + event1.getY() + " to " + event2.getY() + "  = " + Math.abs(event2.getY() - event1.getY()) + " Pixels");
//////        Log.d(DEBUG_TAG, "onFling!  velocityY: " + velocityY);
////
////        // STEP 1: calc fling distance
////        final float distanceY = Math.abs(event2.getY() - event1.getY());
////
////        // STEP 2: check for "realistic" distances
////        if(distanceY < 200 || distanceY > 900){
////            Log.d(DEBUG_TAG, "this swipe was TOO SHORT or TOO LONG");
////            return false;
////        }
////
////        // STEP 3: check for "realistic" velocity
////        if(Math.abs(velocityY) < 900){
////            Log.d(DEBUG_TAG, "this swipe down was TOO SLOW");
////            return false;
////        }
////
////        // STEP 4: UP or DOWN?
////        if(event2.getY() < event1.getY()){ // UP
////
//////            Toast toast = Toast.makeText(ViewFinder.this, "swipe UP detected!", Toast.LENGTH_SHORT);
//////            toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 50);
//////            toast.show();
////
////            if(isShowingSettingsOverlay) {
////                // HIDE settings overlay
////                Animation overlay_fade_out = (Animation) AnimationUtils.loadAnimation(this, R.anim.overlay_fade_out);
////                _settingsOverlay.startAnimation(overlay_fade_out);
////                _settingsOverlay.setVisibility(View.GONE); // hide element
////
////                // hide menu bar button
////                Animation basic_fade_in = (Animation) AnimationUtils.loadAnimation(this, R.anim.basic_fade_in);
////                _btn_show_hide_settings_overlay.startAnimation(basic_fade_in);
////                _btn_show_hide_settings_overlay.setVisibility(View.VISIBLE);
////
////                isShowingSettingsOverlay = false;
////            } else {
////                return false;
////            }
////
////        } else { // DOWN
////
//////            Toast toast = Toast.makeText(ViewFinder.this, "swipe DOWN detected!", Toast.LENGTH_SHORT);
//////            toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 50);
//////            toast.show();
////
////            if(!isShowingSettingsOverlay) {
////
//////                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) _bottomSheet.getLayoutParams();
//////                params.setBehavior(null);
////
//////                params.setBehavior(new AppBarLayout.ScrollingViewBehavior(_bottomSheet.getContext(), null));
////
////                // SHOW settings overlay
////                Animation fade_in = (Animation) AnimationUtils.loadAnimation(this, R.anim.overlay_fade_in);
////                _settingsOverlay.setVisibility(View.VISIBLE); // make element visible
////                _settingsOverlay.startAnimation(fade_in);
////
////                // show menu bar button
////                Animation basic_fade_out = (Animation) AnimationUtils.loadAnimation(this, R.anim.basic_fade_out);
////                _btn_show_hide_settings_overlay.startAnimation(basic_fade_out);
////                _btn_show_hide_settings_overlay.setVisibility(View.GONE);
////
////                isShowingSettingsOverlay = true;
////            } else {
////                return false;
////            }
////        }
////
////        return true;
//
//    }


    @Override
    public boolean onDown(MotionEvent event) {
//        Log.d(DEBUG_TAG,"onDown: " + event.toString());
        return true;
    }

    // ignoring those methods:
    @Override
    public void onLongPress(MotionEvent event) {
//        Log.d(DEBUG_TAG, "onLongPress: " + event.toString());
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) {
//        Log.d(DEBUG_TAG, "onScroll: " + event1.toString() + event2.toString());
        return true;
    }

    @Override
    public void onShowPress(MotionEvent event) {
//        Log.d(DEBUG_TAG, "onShowPress: " + event.toString());
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
//        Log.d(DEBUG_TAG, "onSingleTapUp: " + event.toString());
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }
    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // onFrozenBitmap() Method   -   "event listener"
    //
    // [Part 2] of freezing the camera feed when the user pauses the classification
    // [Part 1] -> see 'CustomAnalyzer.java'
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
    //
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
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



}