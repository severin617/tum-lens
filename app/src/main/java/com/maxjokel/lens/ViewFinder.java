package com.maxjokel.lens;

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
import android.util.Log;
import android.util.Size;
import android.view.HapticFeedbackConstants;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

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

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

public class ViewFinder extends AppCompatActivity {


    // init new Logger instance
    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // CameraX related:   [source: https://developer.android.com/training/camerax/preview#java]
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Camera _camera = null;

    private Preview       _preview  = null; // CameraX use cases for live preview feed
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
//    protected int previewDimY = 480;
    protected int previewDimY = 640;

    // TAKEAWAY: all "Google Models" use 224x224 images as input layer
    // TODO: streamlining
    protected int modelInputX = 224;
    protected int modelInputY = 224;

//    protected Bitmap rgbBitmap = null;

    private long startTimestamp;


    private boolean isCurrentlyClassifying = false;
    private Bitmap emptyBitmap = Bitmap.createBitmap(previewDimX, previewDimY, Bitmap.Config.ARGB_8888);

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // prevent display from being dimmed down
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set corresponding layout
        setContentView(R.layout.activity_view_finder);


        // initialize CameraX
        initCameraX();




        // set up Event Listeners

        // turn flash on or off
        ImageButton btn_flash = findViewById(R.id.btn_flash);
//        btn_flash.setHapticFeedbackEnabled(true);
        btn_flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // TODO: update icon accordingly

                if(_camera.getCameraInfo().hasFlashUnit()){

                    // perform haptic feedback
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                    if(isFlashEnabled){
                        _camera.getCameraControl().enableTorch(false);
                    } else {
                        _camera.getCameraControl().enableTorch(true);
                    }

                    isFlashEnabled = !isFlashEnabled;

                }
            }
        });


        // JUMP TO VALIDATOR VIEW
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






        /**
         * mit allen Netzwerken durchverifiziert -> works :)
         *  - ok: QUANTIZED_MOBILENET
         *  - ok: QUANTIZED_EFFICIENTNET
         *  - ok: INCEPTION_V1 (quantized)
         *  - ok: FLOAT_MOBILENET
         *  - ok: FLOAT_EFFICIENTNET
        * */

        // initialize TF-Lite Classifier
//        Model model = Model.FLOAT_EFFICIENTNET;
//        Model model = Model.FLOAT_MOBILENET;

//        Model model = Model.QUANTIZED_MOBILENET;
        Model model = Model.QUANTIZED_EFFICIENTNET;

//        Model model = Model.INCEPTION_V1;

        Device device = Device.CPU; // Device.CPU; // Device.GPU // Device.NNAPI
        int numThreads = 3;

        try {
            LOGGER.d("Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads);
            classifier = Classifier.create(this, model, device, numThreads);
        } catch (IOException e) { LOGGER.e(e, "Failed to create classifier."); }


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
//                        .setTargetRotation(Surface.ROTATION_180) // warum auch immer...
                        .setTargetRotation(Surface.ROTATION_0) // warum auch immer...
                        .build();


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
                        if(isCurrentlyClassifying){
                            image.close(); // close the image in order to clear the pipeline - IMPORTANT - IMPORTANT - IMPORTANT
                            return;
                        }


                        // update the flag in order to block the image pipeline for the duration of the active classification
                        isCurrentlyClassifying = true;


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
                                        final TextView time = findViewById(R.id.time);
                                        time.setText("this classification took " + startTimestamp + "ms");
                                        System.out.println("+YES at" + SystemClock.uptimeMillis() + " und this classification took " + startTimestamp + "ms");
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




                // Unbind any prior use cases before rebinding the ones we just set up
                cameraProvider.unbindAll();

                // Bind use cases to camera: {Preview, Analysis}
                _camera = cameraProvider.bindToLifecycle(this, cameraSelector, _preview, _analysis);


                // bind and init camera feed to the corresponding object in our layout
                PreviewView viewFinder = findViewById(R.id.viewFinder);
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



// BACKUP 26. JULI:
// rumexperimentiert, quasi der Work in Progress;
// TODO: LÖSCHEN
//
//    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
//    // toBitmap() Method   [source: https://stackoverflow.com/a/58568495]
//    // converts a YUV image to a RGB Bitmap
//
//
//    // das Image img aus dem Analyzer-Use-Case wird hier vom YUV zum RGB Format umgewandelt
//    // dabei wird eine Bitmap erstellt
//    //
//    // AKTUELL: Bitmap-Größe bei Aufruf der Klasse festgelegt;
//    // -> wollen das dynamisch machen, so dass die Größe vom Model abhängig ist
//
//    // Ausschnitt von oben:
//    /*
//        protected int previewDimX = 480;
//        protected int previewDimY = 480;
//
//        ...
//
//        final Bitmap rgbBitmap = toBitmap(img);
//        -> wir returnen also eine Bitmap
//        -> die Größe wird aktuell aus dem IMAGE abgeleitet
//           diese wiederum basiert auf dem Preview bzw. den Werten oben
//
//
//
//     // direkt aus CLASSIFIER.JAVA
//    private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
//        // Loads bitmap into a TensorImage.
//        inputImageBuffer.load(bitmap);
//
//        // Creates processor for the TensorImage.
//        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
//        int numRotation = sensorOrientation / 90;
//        ImageProcessor imageProcessor =
//                new ImageProcessor.Builder()
//                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
//                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
//                        .add(new Rot90Op(numRotation))
//                        .add(getPreprocessNormalizeOp())
//                        .build();
//        return imageProcessor.process(inputImageBuffer);
//    }
//    */
//    private Bitmap toCroppedBitmapRGB(Image image) {
//
//        // workflow
//        //  - convert Image to Bitmap
//        //  - convert YUV to RGB; reduce quality to 0.8
//        //  - crop to square
//        //  - scale down to appropriate model input size
//
//        // IDEA:
//        //  - wahrscheinlich befindet sich das Objekt relativ mittig im Bildausschnitt
//        //    -> Bild quadratisch croppe, mit 80% der kleineren Seite
//        //    -> das an erster Stelle, weil somit weniger Rechenaufwand oder?
//        //  - dann umwandeln
//        //  - dann und runterskalieren
//
////
//        System.out.println("before convert + crop: " + image.getWidth() + "px x " + image.getHeight() + "px");
//
//        // STEP 1: convert YUV to RGB
//        Image.Plane[] planes = image.getPlanes();
//
//        ByteBuffer yBuffer = planes[0].getBuffer();
//        ByteBuffer uBuffer = planes[1].getBuffer();
//        ByteBuffer vBuffer = planes[2].getBuffer();
//
//        int ySize = yBuffer.remaining();
//        int uSize = uBuffer.remaining();
//        int vSize = vBuffer.remaining();
//
//        byte[] nv21 = new byte[ySize + uSize + vSize];
//        //U and V are swapped
//        yBuffer.get(nv21, 0, ySize);
//        vBuffer.get(nv21, ySize, vSize);
//        uBuffer.get(nv21, ySize + vSize, uSize);
//
//        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
////        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 80, out);
//
//
//        // the object of desire is probably in the image center;
//        // so it should not matter, if we loose some pixels near the bezels
//        // -> factor is currently 80% of the smaller side
//        int cropSize = (int)(0.8 * Math.min(image.getWidth(), image.getHeight()));
//
//        // calc offsets
//        int offShortSide = (int)(0.5 * (Math.min(image.getWidth(), image.getHeight()) - cropSize));
//        int offLongSide = (int)(0.5 * (Math.max(image.getWidth(), image.getHeight()) - cropSize));
//
//        // convert to RGB and crop; quality is set to 75%
//        if (image.getWidth() < image.getHeight()){
//            // PORTRAIT
//            yuvImage.compressToJpeg(
//                    new Rect(
//                            offShortSide,                       // left
//                            offLongSide,                        // top
//                            (image.getWidth()-offShortSide),    // right
//                            (image.getHeight()-offLongSide)),   // bottom
//                    75, out);                            // note the byte-buffer at the end of the command
//        } else {
//            // LANDSCAPE
//            yuvImage.compressToJpeg(
//                    new Rect(
//                            offLongSide,                       // left
//                            offShortSide,                      // top
//                            (image.getWidth()-offLongSide),    // right
//                            (image.getHeight()-offShortSide)), // bottom
//                    75, out);
//        }
//
////        THIS ONE WORKS
////        yuvImage.compressToJpeg(new Rect(0, 0, cropSize, cropSize), 80, out); // HIER HINTEN steht, dass in den Byte-Stream geschrieben wird
//
//
//
//        byte[] imageBytes = out.toByteArray();
//        Bitmap temp1 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//
//// KIND OF WORKS :)
////        runOnUiThread( new Runnable() {
////            @Override
////            public void run() {
////                showBitmap(temp1);
////            }
////        });
//
//
//        System.out.println("after convert + crop: " + temp1.getWidth() + "px x " + temp1.getHeight() + "px");
//
//        // STEP 2: resize
////        Bitmap temp2 = Bitmap.createScaledBitmap(temp1, previewDimX, previewDimY, true);
//        Bitmap temp2 = Bitmap.createScaledBitmap(temp1, modelInputX, modelInputY, true);
//
//        System.out.println("after resize: " + temp2.getWidth() + "px x " + temp2.getHeight() + "px");
//
//
//        // STEP 3: crop
//        // TODO: wir wollen das "Kernstück" in der "Mitte"!
//
//        // evtl: int cropDimension =  Math.min(bitmap.getWidth(), bitmap.getHeight());
////        Bitmap temp3 = ThumbnailUtils.extractThumbnail(temp2, previewDimX, previewDimY);
////        Bitmap temp3 = ThumbnailUtils.extractThumbnail(temp2, previewDimX, previewDimX);
//        Bitmap temp3 = ThumbnailUtils.extractThumbnail(temp2, modelInputX, modelInputY);
////        Bitmap temp3 = ThumbnailUtils.extractThumbnail(temp1, modelInputX, modelInputY);
//
////            System.out.println("this is toBitmap(); temp3-Dimensions: " + temp3.getWidth() + "px x " + temp3.getHeight() + "px");
//
//        return temp3;
//
//    }
// BACKUP 25. JULI: funktioniert einwandfrei :)
//    private Bitmap toBitmapWORKING(Image image) {
//
//        Image.Plane[] planes = image.getPlanes();
//
//        ByteBuffer yBuffer = planes[0].getBuffer();
//        ByteBuffer uBuffer = planes[1].getBuffer();
//        ByteBuffer vBuffer = planes[2].getBuffer();
//
//        int ySize = yBuffer.remaining();
//        int uSize = uBuffer.remaining();
//        int vSize = vBuffer.remaining();
//
//        byte[] nv21 = new byte[ySize + uSize + vSize];
//        //U and V are swapped
//        yBuffer.get(nv21, 0, ySize);
//        vBuffer.get(nv21, ySize, vSize);
//        uBuffer.get(nv21, ySize + vSize, uSize);
//
//        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 80, out);
//
//        byte[] imageBytes = out.toByteArray();
//        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//    }
    // end of method
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //fillBytes() method
    //
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    protected void fillBytes(final ImageProxy.PlaneProxy[] planes, final byte[][] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }
    // end of method
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // showResultsInBottomSheet() Method
    //
    // runs in UI Thread
    // shows classification results
    // 15.06 - 17 Uhr

    @UiThread
    protected void showResultsInBottomSheet(List<Classifier.Recognition> results) {

        final TextView desc1 = findViewById(R.id.description1);
        final TextView conf1 = findViewById(R.id.confidence1);

        final TextView desc2 = findViewById(R.id.description2);
        final TextView conf2 = findViewById(R.id.confidence2);

        final TextView desc3 = findViewById(R.id.description3);
        final TextView conf3 = findViewById(R.id.confidence3);

        if (results != null) {

//            System.out.println("found something in the image at " + SystemClock.uptimeMillis());

            // result at index 0
            Classifier.Recognition recognition0 = results.get(0);
            if (recognition0 != null) {
                if (recognition0.getTitle() != null){
                    LOGGER.v("das ist: " + recognition0.getTitle());
                    desc1.setText(recognition0.getTitle());
                }
                if (recognition0.getConfidence() != null){
                    LOGGER.v("Sicherheit: %.2f", (100 * recognition0.getConfidence()));
                    conf1.setText(String.format("%.1f", (100 * recognition0.getConfidence())) + "%");
                }
            }

            // result at index 1
            Classifier.Recognition recognition1 = results.get(1);
            if (recognition1 != null) {
                if (recognition1.getTitle() != null){
                    LOGGER.v("das ist: " + recognition1.getTitle());
                    desc2.setText(recognition1.getTitle());
                }
                if (recognition1.getConfidence() != null){
                    LOGGER.v("Sicherheit: %.2f", (100 * recognition1.getConfidence()));
                    conf2.setText(String.format("%.1f", (100 * recognition1.getConfidence())) + "%");
                }
            }

            // result at index 2
            Classifier.Recognition recognition3 = results.get(2);
            if (recognition3 != null) {
                if (recognition3.getTitle() != null){
                    LOGGER.v("das ist: " + recognition3.getTitle());
                    desc3.setText(recognition3.getTitle());
                }
                if (recognition3.getConfidence() != null){
                    LOGGER.v("Sicherheit: %.2f", (100 * recognition3.getConfidence()));
                    conf3.setText(String.format("%.1f", (100 * recognition3.getConfidence())) + "%");
                }
            }


        } else {
            LOGGER.v("No results.");
        }
    }
    // end of method
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
}