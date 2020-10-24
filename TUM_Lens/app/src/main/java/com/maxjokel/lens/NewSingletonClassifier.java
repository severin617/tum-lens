package com.maxjokel.lens;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.Trace;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import helpers.Logger;

public class NewSingletonClassifier
    implements ClassifierEvents {

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // set up new LOGGER
    private static final Logger LOGGER = new Logger();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    /** SharedPreferences for user configuration */
    private SharedPreferences prefs;

    /** Number of results to show in the UI. */
    private static final int MAX_RESULTS = 5;

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected Interpreter tflite;

    /** Options for configuring the Interpreter. */
    private Interpreter.Options tfliteOptions;

    /** Labels corresponding to the output of the vision model. */
    private List<String> labels;

    /** Image size along the x axis. */
    private int imageSizeX;

    /** Image size along the y axis. */
    private int imageSizeY;

    /** Input image TensorBuffer. */
    private TensorImage inputImageBuffer;

    /** Output probability TensorBuffer. */
    private TensorBuffer outputProbabilityBuffer;

    /** Processer to apply post processing of the output probability. */
    private TensorProcessor probabilityProcessor;

    /** Optional GPU delegate for accleration. */
    private GpuDelegate gpuDelegate = null;

    /** Optional NNAPI delegate for accleration. */
    private NnApiDelegate nnApiDelegate = null;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // everything related to 'ListSingleton'

    ListSingleton listSingletonInstance = ListSingleton.getInstance();
    List<ModelConfig> MODEL_LIST = listSingletonInstance.getList();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // set up initial 'ModelConfig' object and related helper functions

    private ModelConfig modelConfig;
    private TensorOperator getPreprocessNormalizeOp(){
        return modelConfig.getPreprocessNormalizeOp();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -








    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private static CountDownLatch latchThatBlocksINITIALIZATION = new CountDownLatch(0);
    private static CountDownLatch latchThatBlocksCLASSIFICATiON;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // non-final, but static instance
    private static NewSingletonClassifier instance = null;

    // private constructor that can't be accessed from outside
    private NewSingletonClassifier(){

        Trace.beginSection("private NewSingletonClassifier()");
//        LOGGER.i("+++ NEW: trying to exec constructor NewSingletonClassifier()");

        try {
            // wait for a running classification to finnish
            latchThatBlocksINITIALIZATION.await();

            // block any classification until the initialization is done
            latchThatBlocksCLASSIFICATiON = new CountDownLatch(1);
            LOGGER.i("+++ NEW: latchThatBlocksCLASSIFICATiON = new CountDownLatch(1); in constructor for NewSingletonClassifier() -> setup init");

            // initialize classifier object, uses extra thread!!
            initialize();

            latchThatBlocksCLASSIFICATiON.countDown();
            LOGGER.i("+++ NEW: latchThatBlocksCLASSIFICATiON.countdown(); in constructor for NewSingletonClassifier() -> init complete");

        } catch (InterruptedException e) {
            LOGGER.i("+++ NEW: InterruptedException in NewSingletonClassifier()  -> latch");
            e.printStackTrace();
        } catch (IOException e) {
            LOGGER.i("+++ NEW: IOException in NewSingletonClassifier()  -> initialize()");
            e.printStackTrace();
        }

        Trace.endSection();

    }

    // public "constructor" that returns the instance
    public static synchronized NewSingletonClassifier getInstance() {

        if (NewSingletonClassifier.instance == null){
            NewSingletonClassifier.instance = new NewSingletonClassifier();
        }

        return instance;

    }
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -








    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Override
    public void onClassifierConfigChanged(Activity activity) throws IOException {

        LOGGER.i("+++ NEW: received 'configuration changed' event");

        // init new instance
        Trace.beginSection("+++ NEW: onClassifierConfigChanged: instance = new NewSingletonClassifier();");
        NewSingletonClassifier.instance = new NewSingletonClassifier();
        Trace.endSection();

        LOGGER.i("+++ NEW: successfully initialized new instance");

    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public int recognizeImage(Bitmap bitmap){

        try {
            // wait for initialization to finish
            latchThatBlocksINITIALIZATION.await();

            // block any initialization until the classification is done
            latchThatBlocksINITIALIZATION = new CountDownLatch(1);
            LOGGER.i("+++ NEW recognizeImage: latchThatBlocksINITIALIZATION = new CountDownLatch(1);");


            // TODO
            int i = 0;
            i = classify(bitmap);

            latchThatBlocksINITIALIZATION.countDown();
            LOGGER.i("+++ NEW recognizeImage: latchThatBlocksINITIALIZATION.countdown() && returning 0");

            return i;

        } catch (InterruptedException e) {
            e.printStackTrace();
            return -1;
        }

    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -






    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // initialize() method
    //
    // load preferences and set up classifier accordingly
    //
    private void initialize() throws IOException, InterruptedException {

//        // source this out to new thread in order to reduce load on main-thread
        new Thread( new Runnable() { @Override public void run() {

//            LOGGER.i("??? starting new thread");

        // reset, just to make sure
//            close();

        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        LOGGER.i("??? closed tflite");

        if (tfliteModel != null) {
            tfliteModel.clear();
            tfliteModel = null;
        }
        LOGGER.i("??? closed tfliteModel");

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // load sharedPreferences object
//            prefs = App.getContext().getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);

//        LOGGER.i("??? loaded prefs");

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // load saved 'number of threads' from SharedPreferences
//            int saved_threadNumber = getThreadsFromPrefs();
        int saved_threadNumber = 3;

        // load saved 'ProcessingUnit' from SharedPreferences
//            ProcessingUnit saved_processingUnit = getProcessingUnitFromPrefs();
        ProcessingUnit saved_processingUnit = ProcessingUnit.CPU;

        // load saved model from SharedPreferences
//            modelConfig = getModelFromPrefs();
        modelConfig = new ModelConfig();

        LOGGER.i("??? loaded modelCondig");


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // now use this config to set up a new classifier

        Trace.beginSection("loading model");

        // load .tflite file
        try {
//            LOGGER.i("??? trying to load tflitemodel");
            tfliteModel = FileUtil.loadMappedFile(App.getContext(), modelConfig.getModelFilename());
            LOGGER.i("??? successfully loaded tflitemodel");

        } catch (IOException e) {
            LOGGER.e("FEHLER in tfliteModel = FileUtil.loadMappedFile()");
            e.printStackTrace();
        }

        Trace.endSection();
        Trace.beginSection("loading labels");

        // load labels as list
        try {
//            LOGGER.i("??? trying to load label");
            labels = FileUtil.loadLabels(App.getContext(), modelConfig.getLabelFilename());
            LOGGER.i("??? successfully loaded label");
        } catch (IOException e) {
            LOGGER.e("FEHLER in tfliteModel = FileUtil.loadLabels()");
            e.printStackTrace();
        }

        Trace.endSection();

        tfliteOptions = new Interpreter.Options();
        LOGGER.i("??? init new tfliteOptions");


        // set number of threads
        tfliteOptions.setNumThreads(saved_threadNumber);
        LOGGER.i("??? set thread number");

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // finally, create a new interpreter

        Trace.beginSection("initializing new classifier");
        LOGGER.i("??? trying to init 'tflite' new classifier");
        tflite = new Interpreter(tfliteModel, tfliteOptions);
        LOGGER.i("??? successfully init 'tflite'!!");
        Trace.endSection();

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // prepare post-processing

        Trace.beginSection("inititializing postprocessing");

        // read type and shape of input tensors
        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
        imageSizeY = imageShape[1];
        imageSizeX = imageShape[2];

        // read type and shape of output tensors
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        int probabilityTensorIndex = 0;
        int[] probabilityShape = tflite.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUMBER_OF_CLASSES}
        DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();

        // create input tensor
        inputImageBuffer = new TensorImage(imageDataType);

        // create output tensor and its processor
        outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        // create post-processor for the output probability
        probabilityProcessor = new TensorProcessor.Builder().add(modelConfig.getPostprocessNormalizeOp()).build();

        Trace.endSection();


        LOGGER.i("Created a TensorFlow Lite Image Classifier with model '" + modelConfig.getName() + "'");



//        LOGGER.i("is tfliteModel == null??? " + (tfliteModel == null) + "");
//        LOGGER.i("is tflite == null??? " + (tflite == null) + "");


        // classifier is now initialized, reset counter to allow 'recognizeImage()'
//        countDownLatch.countDown();


//        LOGGER.i("+++ SingletonClassifier: notifying listeners!! +++");
//        notifyListeners();


        }}).start(); // END of new thread

//        isBlocked = false;

    } // END OF INITIALIZE - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -





    // #############################################################################################


    private int classify(final Bitmap bitmap) throws InterruptedException {

        LOGGER.i("+++ NEW: classify()    with model '" + modelConfig.getName() + "'");


        if (tflite == null){
            LOGGER.i("#### #### StaticClassifier: tflite is NULL #### #### ");
            return 999;
        }
        if (inputImageBuffer == null){
            LOGGER.i("#### #### StaticClassifier: inputImageBuffer is NULL #### #### ");
            return 888;
        }
        if (outputProbabilityBuffer == null){
            LOGGER.i("#### #### StaticClassifier: outputProbabilityBuffer is NULL #### #### ");
            return 777;
        }



        // Logs this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("loadImage");
        long startTimeForLoadImage = SystemClock.uptimeMillis();

        // load image into new TensorImage
        inputImageBuffer = loadImage(bitmap);

        long endTimeForLoadImage = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));


        Trace.beginSection("runInference");
        long startTimeForReference = SystemClock.uptimeMillis();

        // run inference
        // TODO
        // TODO
        // TODO
        // TODO: ich vermute hier die Ursache
        // TODO: manchmal weisen die Fehlermeldungen auf diese Stelle hin, dass 'Null Object...'
        // TODO: aber eig macht das auch keinen sinn, weil ich das ja vorher abfange?!
        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

        long endTimeForReference = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Timecost to run model inference: " + (endTimeForReference - startTimeForReference));


        // Gets the map of label and probability.
//        Map<String, Float> labeledProbability =
//                new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
//                        .getMapWithFloatValue();
        Trace.endSection();

        return 1;
    }




    /** Loads input image-bitmap, and applies preprocessing.
     *
     * 'ImageProcessor' is a helper feature of the TF-Lite-Package */
    private TensorImage loadImage(final Bitmap bitmap) {

        // load bitmap into TensorImage
        inputImageBuffer.load(bitmap);

        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());

        // create new TF-Lite ImageProcessor to convert from Bitmap to TensorImage
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(getPreprocessNormalizeOp())
                        .build();

        return imageProcessor.process(inputImageBuffer);
    }



}