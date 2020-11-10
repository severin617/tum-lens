package com.maxjokel.lens.helpers;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    13.09.2020, 19:45

    We use this custom CameraX Analyzer to display the last camera view frame as 'frozen'
    when the user pauses the classification.

    While there (were) are several approaches to this, this one is apparently the one suggested
    by the Google developer team.

    IDEA
     - adapted from here: https://stackoverflow.com/a/59674075
     - we use a callback interface to communicate between 'ViewFinder.java' and this class
     - here, an 'event' is fired   [Part 1]
     - it is then processed in 'ViewFinder.java', where we actually freeze the UI   [Part 2]

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class FreezeAnalyzer implements ImageAnalysis.Analyzer {


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // init new Logger instance
    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // init global variables
    private FreezeCallback callback;
    private boolean isFrozen;
    private int lens_front_back = 0; // [0 = back, 1 = front]; set 'back' as default;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // constructor
    public FreezeAnalyzer(FreezeCallback freezeCallback){
        this.isFrozen = false;
        this.callback = freezeCallback;
    }


    @Override
    public void analyze(@NonNull ImageProxy image) {

        // analyze() does nothing but closing the image;
        // if the 'isFrozen' flag is set, it will return a bitmap within its callback

        if (isFrozen){

            // convert the CameraX YUV ImageProxy to a (RGB?) bitmap
            @androidx.camera.core.ExperimentalGetImage
            Image img = image.getImage();

            @SuppressLint("UnsafeExperimentalUsageError")
            Bitmap bmp = ImageUtils.toBitmap(img, lens_front_back, image.getImageInfo().getRotationDegrees());

            // trigger new callback
            callback.onFrozenBitmap(bmp);

            // reset flag
            isFrozen = false;

        }

        // close image to continue processing the next frames
        image.close();
    }



    public void freeze(int lens){
        isFrozen = true;
        lens_front_back = lens;
    }

}
