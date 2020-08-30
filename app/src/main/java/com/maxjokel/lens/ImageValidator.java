package com.maxjokel.lens;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.HapticFeedbackConstants;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import helpers.Logger;
import tflite.Classifier;


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    26.07.2020, 10:45

    - mainly for debugging, in order to verify the image pre-processing
    - shows end result of cropping and scaling the view finder


// TODO: aufräumen!


+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class ImageValidator extends AppCompatActivity {

    // init new Logger instance
    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // CameraX related:   [source: https://developer.android.com/training/camerax/preview#java]
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Camera _camera = null;

    private Preview _preview  = null; // CameraX use cases for live preview feed
    private ImageAnalysis _analysis = null; // CameraX use cases for actual classification

    private ExecutorService _cameraExecutorForAnalysis = null;

    private Boolean isFlashEnabled = false;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // TF-Lite related to IMAGE UTILS:   [source: TF-Lite example app]
    protected byte[][] yuvBytes = new byte[3][];
    protected int[] rgbBytes = null;
    protected int yRowStride;
    private Runnable imageConverterRunnable;


    // TF-Lite related to CLASSIFICATION:   [source: TF-Lite example app]
    private Classifier classifier;

    protected int previewDimX = 480;
    protected int previewDimY = 480;

    protected int modelInputX = 224;
    protected int modelInputY = 224;

    private boolean isCurrentlyClassifying = false;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // set status bar background to black
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.black));

        setContentView(R.layout.activity_image_validator);

        // initialize CameraX
        initCameraX();

    }

    @Override
    public void onBackPressed() {
//        super.onBackPressed();
        // we need to launch the view finder activity explicitly
        Intent intent = new Intent(ImageValidator.this, ViewFinder.class);
        startActivity(intent);
        finish();

    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // initCameraX() Method
    // initializes CameraX and with that the live preview and image analyzer

    private void initCameraX() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {

                // init new camera provider
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // select the back facing lens
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();


                // init preview object that holds the camera live feed
                _preview = new Preview.Builder()
                        .setTargetResolution(new Size(previewDimX, previewDimY)) // damit ist alles DEUTLICH SCHNELLER!!
//                        .setTargetRotation(Surface.ROTATION_90) // warum auch immer...
//                        .setTargetRotation(Surface.ROTATION_180) // warum auch immer...
//                        .setTargetRotation(Surface.ROTATION_0) // hat keinen Effekt...
                        .build();


                // init analysis object that converts every frame to a RGB bitmap and gives it to the classifier
                _analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(previewDimX, previewDimY)) // 06.06.2020, 18 Uhr: Bitmap muss QUADRATISCH sein
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // async
//                        .setTargetRotation(Surface.ROTATION_90) // warum auch immer...
//                        .setTargetRotation(Surface.ROTATION_180) // warum auch immer...
//                        .setTargetRotation(Surface.ROTATION_0) // hat keinen Effekt...
                        .build();

                // set up the image analysis use case
                _cameraExecutorForAnalysis = Executors.newSingleThreadExecutor();
                _analysis.setAnalyzer(_cameraExecutorForAnalysis, new ImageAnalysis.Analyzer() {

                    // - - - - - - - - - - - - - - - - - - - - - - - - - -
                    @SuppressLint("UnsafeExperimentalUsageError")
                    @Override
                    public void analyze(@NonNull ImageProxy image) {

                        // do not accept additional images if there is already a classification running
                        if(isCurrentlyClassifying){
                            image.close(); // close the image in order to clear the pipeline - IMPORTANT - IMPORTANT - IMPORTANT
                            return;
                        }

                        // update the flag in order to block the image pipeline for the duration of the active classification
                        isCurrentlyClassifying = true;

                        // convert the CameraX YUV ImageProxy to RGB bitmap
                        @androidx.camera.core.ExperimentalGetImage
                        Image img = image.getImage();


                    long startTimeForLoadImage = SystemClock.uptimeMillis();

                        final Bitmap rgbBitmap = toCroppedRGBBitmap(img);

                    long endTimeForLoadImage = SystemClock.uptimeMillis();
                    System.out.println("Timecost to convert from Image to Bitmap: " + (endTimeForLoadImage - startTimeForLoadImage) + "\n");


                        isCurrentlyClassifying = false;
                        image.close();
                        return;

                    }
                    // end of method - - - - - - - - - - - - - - - - - - -
                });




                // Unbind any prior use cases before rebinding the ones we just set up
                cameraProvider.unbindAll();

                // Bind use cases to camera: {Preview, Analysis}
                _camera = cameraProvider.bindToLifecycle(this, cameraSelector, _preview, _analysis);


                // bind and init camera feed to the corresponding object in our layout
                PreviewView viewFinder = findViewById(R.id.feed);
                _preview.setSurfaceProvider(viewFinder.createSurfaceProvider()); // def camerax_version = "1.0.0-beta04"
                //_preview.setSurfaceProvider(viewFinder.createSurfaceProvider(_camera.getCameraInfo())); // def camerax_version = "1.0.0-beta03"


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

        Bitmap temp3 = Bitmap.createBitmap(temp2, 0, 0, temp2.getWidth(), temp2.getHeight(), matrix, true);

//        System.out.println("after scale + rotate: " + temp3.getWidth() + "px x " + temp3.getHeight() + "px\n");

        // display result of transformation in bottom ImageView
        runOnUiThread( new Runnable() { @Override public void run() { showBitmap(temp3); } });

        // Backup: ThumbnailUtils.extractThumbnail()

        return temp3;

    }
    // end of method - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @UiThread
    private void showBitmap(Bitmap b) {
        ImageView iv = findViewById(R.id.converted);
        iv.setImageBitmap(b);
    }

}