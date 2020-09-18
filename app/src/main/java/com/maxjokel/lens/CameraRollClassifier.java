package com.maxjokel.lens;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import helpers.Logger;
import tflite.Classifier;


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    18.07.2020

    this activity lets you pick an image from camera roll and will classify it


    ---

    adapted from here: https://stackoverflow.com/a/2636538


+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class CameraRollClassifier extends AppCompatActivity {

    // init new Logger instance
    private static final Logger LOGGER = new Logger();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    // TF-Lite related to CLASSIFICATION:   [source: TF-Lite example app]
    private Classifier classifier;


    // TAKEAWAY: all "Google Models" use 224x224 images as input layer
    // TODO: streamlining
    protected int modelInputX = 224;
    protected int modelInputY = 224;

    private long startTimestamp;

    private boolean isCurrentlyClassifying = false;
    private boolean isClassificationPaused = false;


    protected Classifier.Model _model = Classifier.Model.FLOAT_MOBILENET;
    protected int _numberOfThreads = 3; // default is 3
    protected Classifier.Device _processingUnit = Classifier.Device.CPU; // CPU is default


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // instantiate new SharedPreferences object
    SharedPreferences prefs = null;
    SharedPreferences.Editor prefEditor = null;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    // this is the action code we use in our intent,
    // this way we know we're looking at the response from our own action
    private static final int SELECT_PICTURE = 1;



    private boolean _isInitialCall = true;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        // load sharedPreferences object and set up editor
        prefs = getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);
        prefEditor = prefs.edit();



        // set status bar background to black
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.black));




        setContentView(R.layout.activity_camera_roll_classifier);






        // TODO: BottomSheet handler
        // TODO: braucht's das überhaupt?


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

        if(saved_device == Classifier.Device.GPU.hashCode()){
            _processingUnit = Classifier.Device.GPU;
        } else if(saved_device == Classifier.Device.NNAPI.hashCode()){
            _processingUnit = Classifier.Device.NNAPI;
        } else { // use CPU as default
            _processingUnit = Classifier.Device.CPU;
        }

        String cName = "chip_" + _processingUnit.name();
        int cId = getResources().getIdentifier(cName, "id", getPackageName());
        Chip c = findViewById(cId);
        c.setChecked(true);


        // model
        int helper = 0;
        int saved_model = prefs.getInt("model", 0);

        if(saved_model == Classifier.Model.QUANTIZED_MOBILENET.hashCode()){
            _model = Classifier.Model.QUANTIZED_MOBILENET;
            helper = 2;
        } else if (saved_model == Classifier.Model.FLOAT_EFFICIENTNET.hashCode()) {
            _model = Classifier.Model.FLOAT_EFFICIENTNET;
            helper = 3;
        } else if (saved_model == Classifier.Model.QUANTIZED_EFFICIENTNET.hashCode()) {
            _model = Classifier.Model.QUANTIZED_EFFICIENTNET;
            helper = 4;
        } else if (saved_model == Classifier.Model.INCEPTION_V1.hashCode()) {
            _model = Classifier.Model.INCEPTION_V1;
            helper = 5;
        } else if (saved_model == Classifier.Model.INCEPTION_V1_selfConverted.hashCode()) {
            _model = Classifier.Model.INCEPTION_V1_selfConverted;
            helper = 6;
        } else { // set FLOAT_MOBILENET as default
            _model = Classifier.Model.FLOAT_MOBILENET;
            helper = 1;
        }

        // set initial RadioButton selection
        String rName = "radioButton" + helper;
        int rId = getResources().getIdentifier(rName, "id", getPackageName());
        RadioButton r = findViewById(rId);
        r.setChecked(true);


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                  INIT CORE FEATURES                   +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        updateThreadCounter();

        reInitClassifier();







    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +                SET UP EVENT LISTENERS                 +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // bind event listener to button; opens camera roll
        findViewById(R.id.ll_pick_image).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                if(_isInitialCall){

                    // perform haptic feedback
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                    // in onCreate or any event where your want the user to select a file
                    Intent intent = new Intent();
                    intent.setType("image/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);

                }

            }
        });

        findViewById(R.id.btn_start_over).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // perform haptic feedback
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                // in onCreate or any event where your want the user to select a file
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);

            }
        });


    // decrease or increase number of threads
        findViewById(R.id.btn_threads_minus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                if(_numberOfThreads > 1){ _numberOfThreads--; }
                updateThreadCounter();
                reInitClassifier();
            }
        });
        findViewById(R.id.btn_threads_plus).setOnClickListener(new View.OnClickListener() {
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
                        _model = Classifier.Model.FLOAT_MOBILENET;
                        break;
                    case R.id.radioButton2:
                        _model = Classifier.Model.QUANTIZED_MOBILENET;
                        break;
                    case R.id.radioButton3:
                        _model = Classifier.Model.FLOAT_EFFICIENTNET;
                        break;
                    case R.id.radioButton4:
                        _model = Classifier.Model.QUANTIZED_EFFICIENTNET;
                        break;
                    case R.id.radioButton5:
                        _model = Classifier.Model.INCEPTION_V1;
                        break;
                    case R.id.radioButton6:
                        _model = Classifier.Model.INCEPTION_V1_selfConverted;
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
                        _processingUnit = Classifier.Device.CPU;
                        break;
                    case R.id.chip_GPU:
                        _processingUnit = Classifier.Device.GPU;
                        break;
                    case R.id.chip_NNAPI:
                        _processingUnit = Classifier.Device.NNAPI;
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




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // onActivityResult() method
    //
    // is called once an image from camera roll is selected
    // fetches image from disk and transforms it to bitmap

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {

            if (requestCode == SELECT_PICTURE) {

                Uri selectedImageUri = data.getData();

                // load image as bitmap
                Bitmap bmp = null;
                try {
                    bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // read exif info to handle image rotation correctly
                //      [adapted from here: https://stackoverflow.com/a/4105966]
                //      [and here:          https://stackoverflow.com/a/42937272]
                try {
                    InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                    ExifInterface exif = new ExifInterface(inputStream);
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                    LOGGER.d("EXIF", "Exif: " + orientation);

                    // rotate accordingly
                    Matrix matrix = new Matrix();

                    if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                        matrix.postRotate(90);
                    }
                    else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                        matrix.postRotate(180);
                    }
                    else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                        matrix.postRotate(270);
                    }

                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

                } catch (IOException e) {
                    e.printStackTrace();
                }

                // display and classify
                if (bmp != null) {

                    ImageView iv = findViewById(R.id.bmp_camera_roll);
                    iv.setImageBitmap(bmp);

                    // start classification
                    classify(bmp);

                } else {
                    // TODO
                    System.out.println("### ### ### ### BITMAP IS NULL ###");
                }



                // adjust UI: show button to select another image
                if(_isInitialCall){
                    _isInitialCall = false;
                    findViewById(R.id.btn_start_over).setVisibility(View.VISIBLE);
                }

            }
        }
    }



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    public void classify(Bitmap bitmap){


        // IDEA: the object of desire is probably in the image center;
        // so it should not matter, if we loose some pixels near the bezels
        // -> factor is currently 90% of the smaller side
        int cropSize = (int)(0.9 * Math.min(bitmap.getWidth(), bitmap.getHeight()));

        // calc offsets for cropping
        int offShortSide = (int)(0.5 * (Math.min(bitmap.getWidth(), bitmap.getHeight()) - cropSize));
        int offLongSide = (int)(0.5 * (Math.max(bitmap.getWidth(), bitmap.getHeight()) - cropSize));

        Bitmap temp1 = null;

        // crop to square
        if (bitmap.getWidth() < bitmap.getHeight()){
            // PORTRAIT
            temp1 = Bitmap.createBitmap(bitmap,
                    offShortSide,                       // left
                    offLongSide,                        // top
                    (bitmap.getWidth() - offShortSide),   // right
                    (bitmap.getHeight() - offLongSide));  // bottom
        } else {
            // LANDSCAPE
            temp1 = Bitmap.createBitmap(bitmap,
                    offLongSide,
                    offShortSide,
                    (bitmap.getWidth() - offLongSide),
                    (bitmap.getHeight() - offShortSide));
        }


        // compress, quality is set to 35%
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        temp1.compress(Bitmap.CompressFormat.JPEG, 35, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap temp2 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);


        // scale down to required dimensions
        Bitmap temp3 = Bitmap.createScaledBitmap(temp2, modelInputX, modelInputY, true);

        // rotate by 90 degrees
        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        Bitmap bmp = Bitmap.createBitmap(temp3, 0, 0, temp3.getWidth(), temp3.getHeight(), matrix, true);


        // + + +





        // and pass the frame to the classifier that is started in a new thread
        AsyncTask.execute(new Runnable() {   // [source: https://stackoverflow.com/a/31549559]
            @Override
            public void run() {
                final long startTime = SystemClock.uptimeMillis();
                final List<Classifier.Recognition> results = classifier.recognizeImage(bmp, 0);
                startTimestamp = SystemClock.uptimeMillis() - startTime;

                runOnUiThread( new Runnable() {
                    @Override
                    public void run() {
                        showResultsInBottomSheet(results);

                        final TextView time = findViewById(R.id.time);
                        time.setText("this classification took " + startTimestamp + "ms");
                    }
                });

                // now that the classification is done, reset the flag
                isCurrentlyClassifying = false;
            }
        });



    }





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
    // reInitClassifier() Method
    //
    // will try to instantiate a new TF-Lite classifier

    protected void reInitClassifier(){
        try {
            LOGGER.d("Creating classifier (model=%s, device=%s, numThreads=%d)", _model, _processingUnit, _numberOfThreads);

            classifier = Classifier.create(this, _model, _processingUnit, _numberOfThreads);

        } catch (IOException e) {
            LOGGER.e(e, "Failed to create classifier.");
        }
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


        findViewById(R.id.placeholder).setVisibility(View.GONE);
        findViewById(R.id.actual_result).setVisibility(View.VISIBLE);


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
    // end of method
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -





    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // intercept 'back button pressed' event


    @Override
    public void onBackPressed() {
        // we need to launch the view finder activity explicitly
        Intent intent = new Intent(CameraRollClassifier.this, ViewFinder.class);
        startActivity(intent);
        finish();
    }




}
