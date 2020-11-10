package com.maxjokel.lens.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    static helper class that holds methods for image transformation

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */


public final class ImageUtils {

    // init new Logger instance
    private static final Logger LOGGER = new Logger();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // width of the cropped square region based on the smaller dimension of the input
    private static final double FACTOR = 0.9;

    // compression quality
    private static final int QUALITY = 35;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // toCroppedBitmap() Method
    // converts a passed Image to RGB Bitmap; used in 'ViewFinder' activity;
    //
    // Workflow
    //  - converts YUV image to RGB bitmap:
    //  - reduces quality to 35%
    //  - crops to square (length is determined by shortest side of image)
    //
    // Findings
    //  - "quality" doesn't seem to have a huge effect on processing time:
    //    both, 25% and 50% run in sub-20ms time frames
    //  - no real effect on prediction accuracy noticeable
    //
    public static Bitmap toCroppedBitmap(Image image, int rotation){

        return converter(image, 35, true, rotation);

    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // toBitmap() Method
    // converts a passed Image to RGB Bitmap (without cropping, scaling); used in 'FreezeAnalyzer';
    //
    // Workflow
    //  - converts YUV image to RGB bitmap:
    //
    public static Bitmap toBitmap(Image image, int lens_front_back, int rotation){

        Bitmap temp = converter(image, 100, false, rotation);

        // WORKAROUND
        // on Pixel 3a it is necessary to mirror the image when using the front facing camera...
        Matrix matrix = new Matrix();
        if(lens_front_back != 0){
            matrix.preScale(-1.0f, 1.0f);
        }

        return Bitmap.createBitmap(temp, 0, 0, temp.getWidth(), temp.getHeight(), matrix, true);

    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // converter() Method
    // "backed" for the methods above
    //
    // Workflow
    //  - convert YUV image to RGB bitmap:
    //  - reduce quality as specified in argument
    //  - crops to square (length is determined by shortest side of image)
    //
    private static Bitmap converter(Image image, int quality, boolean cropSquare, int rotation){

        if (quality > 100 || quality < 1){
            quality = QUALITY;
        }

//        LOGGER.d("before convert + crop: " + image.getWidth() + "px x " + image.getHeight() + "px");

        // convert YUV image to RGB bitmap and crop   [source: https://stackoverflow.com/a/58568495]
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

        // create new Rect for cropping; by default full width and height
        Rect rect = new Rect(0, 0, image.getWidth(), image.getHeight());


        if(cropSquare){ // if crop is set to true

            // IDEA: the object of desire is probably in the image center;
            // so it should not matter, if we loose some pixels near the bezels
            // -> factor is currently 90% of the smaller side
            int cropSize = (int)(FACTOR * Math.min(image.getWidth(), image.getHeight()));

            // calc offsets for cropping
            int offShortSide = (int)(0.5 * (Math.min(image.getWidth(), image.getHeight()) - cropSize));
            int offLongSide = (int)(0.5 * (Math.max(image.getWidth(), image.getHeight()) - cropSize));

            // set up crop
            if (image.getWidth() < image.getHeight()){
                // PORTRAIT
                new Rect(
                        offShortSide,                       // left
                        offLongSide,                        // top
                        (image.getWidth()-offShortSide),    // right
                        (image.getHeight()-offLongSide));   // bottom
            } else {
                // LANDSCAPE
                new Rect(offLongSide, offShortSide, (image.getWidth()-offLongSide), (image.getHeight()-offShortSide));
            }

        }

        // convert to RGB
        yuvImage.compressToJpeg(rect, quality, out);

        // finally, create bitmap
        byte[] imageBytes = out.toByteArray();
        Bitmap temp1 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

//        LOGGER.d("after convert + crop: " + temp1.getWidth() + "px x " + temp1.getHeight() + "px");

        // rotate image by passed value
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);

        return Bitmap.createBitmap(temp1, 0, 0, temp1.getWidth(), temp1.getHeight(), matrix, true);

    }



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // cropBitmap() Method
    // crops passed Bitmap to square; used in 'CameraRoll' activity
    //
    // Workflow
    //  - reduces quality to 35%
    //  - crops to square (length is determined by shortest side of image)
    //
    public static Bitmap cropBitmap(Bitmap bitmap){

        // IDEA: the object of desire is probably in the image center;
        // so it should not matter, if we loose some pixels near the bezels
        // -> factor is currently 90% of the smaller side
        int cropSize = (int)(FACTOR * Math.min(bitmap.getWidth(), bitmap.getHeight()));

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
        temp1.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap temp2 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        return Bitmap.createBitmap(temp2, 0, 0, temp2.getWidth(), temp2.getHeight());

    }
    // END - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


}
