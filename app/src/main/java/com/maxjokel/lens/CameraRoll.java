package com.maxjokel.lens;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import helpers.ImageUtils;
import helpers.Logger;


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    this activity lets you pick an image from camera roll and will classify it


    ---

    adapted from here: https://stackoverflow.com/a/2636538


+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class CameraRoll extends AppCompatActivity implements ClassifierEvents {

    // init new Logger instance
    private static final Logger LOGGER = new Logger();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // TF-Lite related
    private Classifier classifier;
    private long startTimestamp;

    // holds the last image, so that when the model is changed we can re-run classification
    private Bitmap savedBitmap = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // instantiate new SharedPreferences object
    SharedPreferences prefs = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // Fragment in BottomSheet that displays the classification results
    CameraRollPredictionsFragment predictionsFragment = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // this is the action code we use in our intent,
    // this way we know we're looking at the response from our own action
    private static final int SELECT_PICTURE = 1;
    private boolean _isInitialCall = true;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // load sharedPreferences object and set up editor
        prefs = getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);


        // prevent display from being dimmed down
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set status bar background to black
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.black));


        // set corresponding layout
        setContentView(R.layout.activity_camera_roll);




        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +             SETUP BOTTOM SHEET FRAGMENTS              +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init new Fragment Instances
        ModelSelectorFragment msf = ModelSelectorFragment.newInstance();
        predictionsFragment = CameraRollPredictionsFragment.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.add(R.id.modelselector_container, msf, "msf");
        fragmentTransaction.add(R.id.results_container, predictionsFragment, "predictionsFragment");
        fragmentTransaction.commit();

        // for transmitting events back from the fragment to this class
        msf.addListener(this);



        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                   INIT CLASSIFIER                     +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

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

        // now, close the classifier ...
        classifier.close();

        // ... and try to re-int it
        reInitClassifier();


        // if there is a saved Bitmap, re-run the classification
        if(savedBitmap != null) {
            classify(savedBitmap);
        }



    } // END of onClassifierConfigChanged - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // reInitClassifier() Method
    // will try to instantiate a new TF-Lite classifier
    private void reInitClassifier(){

        try {
            classifier = new Classifier(this);
        } catch (IOException e) {
            LOGGER.e("Error occured while trying to re-init the classifier: " + e);
            e.printStackTrace();
        }

    }


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
//                    LOGGER.d("EXIF", "Exif: " + orientation);

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

                // display, then crop and classify
                if (bmp != null) {

                    // render to screen
                    ImageView iv = findViewById(R.id.bmp_camera_roll);
                    iv.setImageBitmap(bmp);

                    // use helper class to crop the bitmap to square
                    Bitmap croppedBitmap = ImageUtils.cropBitmap(bmp);
                    savedBitmap = croppedBitmap;

                    // start classification
                    classify(croppedBitmap);

                } else {
                    LOGGER.e("Error occurred while processing bitmap in CameraRoll: bitmap is null!");
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

        final long startTime = SystemClock.uptimeMillis();

        // run inference on image
        final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

        startTimestamp = SystemClock.uptimeMillis() - startTime;

        runOnUiThread( new Runnable() {
            @Override
            public void run() {
                // pass list to fragment, that renders the recognition results to UI
                predictionsFragment.showRecognitionResults(results, startTimestamp);
            }
        });

    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // intercept 'back button pressed' event
    @Override
    public void onBackPressed() {

        savedBitmap = null;

        classifier.close();
        classifier = null;

        // we need to launch the view finder activity explicitly
        Intent intent = new Intent(CameraRoll.this, ViewFinder.class);
        startActivity(intent);
        finish();
    }
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

}
