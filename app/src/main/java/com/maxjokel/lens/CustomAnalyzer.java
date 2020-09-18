package com.maxjokel.lens;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.media.Image;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import helpers.Logger;

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


// new callback interface, to react to changes in 'viewFinder.java'
// [reference: https://stackoverflow.com/a/18054865]
interface FreezeCallback {
    public void onFrozenBitmap(Bitmap b);
}

public class CustomAnalyzer implements ImageAnalysis.Analyzer {


    // init new Logger instance
    private static final Logger LOGGER = new Logger();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // init global variables
    protected FreezeCallback callback;
    protected boolean isFrozen;
    protected int lens_front_back = 0; // [0 = back, 1 = front]; set 'back' as default;

    // constructor
    public CustomAnalyzer(FreezeCallback fcb){
        this.isFrozen = false;
        this.callback = fcb;
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
            Bitmap bmp = toBitmap(img, image.getImageInfo().getRotationDegrees());

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

    // converts YUV Image to RGB bitmap
    private Bitmap toBitmap(@NonNull Image image, int r) {

        // rotate image according to the orientation
        int rotation = 0;
        rotation = r;
        System.out.println("Rotation (in degrees): " + rotation);

        // TODO: this does not make sense
        // TODO: this does not make sense
        // TODO: this does not make sense

        // TODO: we need to know, if the active lens is facing
        //    - 'forwards'  -> apply mirroring
        //    - 'backwards' -> do nothing



        // STEP 1: convert YUV image to RGB bitmap   [source: https://stackoverflow.com/a/58568495]
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


        // convert to RGB and crop; quality is set to 100%
        yuvImage.compressToJpeg(
                new Rect(
                        0,                      // left
                        0,                      // top
                        image.getWidth(),           // right
                        image.getHeight()),         // bottom
                100, out);                   // note the byte-buffer at the end of the command


        // finally, create bitmap
        byte[] imageBytes = out.toByteArray();
        Bitmap temp1 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);


        // rotate
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);

        // TODO
        // this makes no sense at all, but this workaround is necessary to re-mirror the frozen frame...
        if(lens_front_back != 0){
            matrix.preScale(1.0f, -1.0f);
        }

        return Bitmap.createBitmap(temp1, 0, 0, temp1.getWidth(), temp1.getHeight(), matrix, true);

    }

}
