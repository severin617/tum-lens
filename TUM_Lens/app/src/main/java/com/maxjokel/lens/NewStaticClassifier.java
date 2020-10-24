package com.maxjokel.lens;

import android.app.Activity;
import android.content.Context;
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
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;

import helpers.Logger;

public class NewStaticClassifier {


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // set up new LOGGER
    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    /** SharedPreferences for user configuration */
    private static SharedPreferences prefs;

    /** Number of results to show in the UI. */
    private static final int MAX_RESULTS = 5;

    /** The loaded TensorFlow Lite model. */
    private static MappedByteBuffer MODELBUFFER;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected static Interpreter INTERPRETER;

    /** Options for configuring the Interpreter. */
    private static Interpreter.Options tfliteOptions;

    /** Labels corresponding to the output of the vision model. */
    private static List<String> labels;

    /** Image size along the x axis. */
    private static int imageSizeX;

    /** Image size along the y axis. */
    private static int imageSizeY;

    /** Input image TensorBuffer. */
    private static TensorImage inputImageBuffer;

    /** Output probability TensorBuffer. */
    private static TensorBuffer outputProbabilityBuffer;

    /** Processer to apply post processing of the output probability. */
    private static TensorProcessor probabilityProcessor;

    /** Optional GPU delegate for accleration. */
    private static GpuDelegate gpuDelegate = null;

    /** Optional NNAPI delegate for accleration. */
    private static NnApiDelegate nnApiDelegate = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // everything related to 'ListSingleton'

    private static ListSingleton listSingletonInstance = ListSingleton.getInstance();
    private static List<ModelConfig> MODEL_LIST = listSingletonInstance.getList();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // set up initial 'ModelConfig' object and related helper functions

    private static ModelConfig modelConfig;
    private static TensorOperator getPreprocessNormalizeOp(){
        return modelConfig.getPreprocessNormalizeOp();
    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // set up CountDownLatches that help us to manage the control flow

    private static CountDownLatch latchThatBlocksINITIALIZATION = new CountDownLatch(0);
    private static CountDownLatch latchThatBlocksCLASSIFICATiON;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // constructor

    // non-final, but static instance
    private static NewStaticClassifier instance = null;

    // private constructor that can't be accessed from outside
    private NewStaticClassifier(){

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
        }

        Trace.endSection();

    }

    // public "constructor" that returns the instance
    public static synchronized NewStaticClassifier getInstance() {

        if (NewStaticClassifier.instance == null){
            NewStaticClassifier.instance = new NewStaticClassifier();
        }

        return instance;

    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // methods

    /** This method is called whenever a fragment detects a change in configuration through the user
     *
     * */
    public static void onConfigChanged(){

        LOGGER.i("+++ NEW: received 'onConfigChanged' event [FRAGMENT]");

        // init new instance
        Trace.beginSection("+++ NEW: onClassifierConfigChanged: instance = new NewSingletonClassifier();");
        NewStaticClassifier.instance = new NewStaticClassifier();
        Trace.endSection();

        LOGGER.i("+++ NEW: successfully initialized new instance");

    }



    /** Wrapper method for image classification
     *
     * waits for any ongoing initializations to finish;
     * sets a new Latch to block any other initializations and runs inference;
     * returns a list of the top-n Recognition objects;
     *
     * @param: bitmap:      a RGB bitmap
     *
     * */
    public static List<Recognition> recognizeImage(Bitmap bitmap){

        try {
            // wait for initialization to finish
            latchThatBlocksINITIALIZATION.await();

            // block any initialization until the classification is done
            latchThatBlocksINITIALIZATION = new CountDownLatch(1);
            LOGGER.i("+++ NEW recognizeImage: latchThatBlocksINITIALIZATION = new CountDownLatch(1);");


            // make sure Bitmap is not null
            if(bitmap == null) throw new Exception();


            // run classification on bitmap
            List<Recognition> recognitions = classify2(bitmap);


            latchThatBlocksINITIALIZATION.countDown();
            LOGGER.i("+++ NEW recognizeImage: latchThatBlocksINITIALIZATION.countdown() && returning 0");

            return recognitions;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

    }



    /** - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
     *
     * This method initializes the global 'tflite' and 'tfliteModel' objects with regards to the
     * the user's configuration;
     *
     * The method utilizes a separate thread in order to reduce load on the main-thread that runs
     * the application;
     *
     * */
    private static void initialize() {

        LOGGER.i("+++ NEW, initialize():  starting new thread");

        // utilize new thread
        new Thread( new Runnable() { @Override public void run() {

            // reset Interpreter
            if (INTERPRETER != null) {
                INTERPRETER.close();
                INTERPRETER = null;
            }
            LOGGER.i("+++ NEW, initialize(): reset Interpreter");

            // reset MappedByteBuffer
            if (MODELBUFFER != null) {
                MODELBUFFER.clear();
                MODELBUFFER = null;
            }
            LOGGER.i("+++ NEW, initialize(): reset MappedByteBuffer");

            // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +


            // load sharedPreferences
            prefs = App.getContext().getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);
            LOGGER.i("+++ NEW, initialize(): successfully loaded sharedPreferences");


            // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
            // load configuration from SharedPreferences

            // load saved 'number of threads' from SharedPreferences
            int saved_threadNumber = getThreadsFromPrefs();
//            LOGGER.i("+++ NEW, initialize(): successfully loaded saved_threadNumber");


            // load saved 'ProcessingUnit' from SharedPreferences
             ProcessingUnit saved_processingUnit = getProcessingUnitFromPrefs();
//            ProcessingUnit saved_processingUnit = ProcessingUnit.CPU;
//            LOGGER.i("+++ NEW, initialize(): successfully loaded saved_processingUnit");


            // load saved model from SharedPreferences
            modelConfig = getModelFromPrefs();
//            modelConfig = new ModelConfig();
//            LOGGER.i("+++ NEW, initialize(): successfully loaded modelConfig");


            // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
            // load files from '/assets'

            // load .tflite file from '/assets'
            Trace.beginSection("loading file into MODELBUFFER");
            try {
                MODELBUFFER = FileUtil.loadMappedFile(App.getContext(), modelConfig.getModelFilename());
                LOGGER.i("+++ NEW, initialize(): successfully loaded tflite file into MODELBUFFER");
            } catch (IOException e) {
                LOGGER.i("### NEW, initialize(): FAILED to load tflite file into MODELBUFFER");
                e.printStackTrace();
            }
            Trace.endSection();


            // load labels file from '/assets' as list
            Trace.beginSection("loading labels");
            try {
                labels = FileUtil.loadLabels(App.getContext(), modelConfig.getLabelFilename());
                LOGGER.i("+++ NEW, initialize(): successfully loaded label file");
            } catch (IOException e) {
                LOGGER.i("### NEW, initialize(): FAILED to load labels");
                e.printStackTrace();
            }
            Trace.endSection();


            // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
            // set up classifier options

            tfliteOptions = new Interpreter.Options();

            // set number of threads
            tfliteOptions.setNumThreads(saved_threadNumber);
            LOGGER.i("+++ NEW, initialize(): successfully set number of threads");

            // set processing unit {CPU, GPU, NNAPI}
            switch (saved_processingUnit) {
                case NNAPI:
                    nnApiDelegate = new NnApiDelegate();
                    tfliteOptions.addDelegate(nnApiDelegate);
                    break;
                case GPU:
                    gpuDelegate = new GpuDelegate();
                    tfliteOptions.addDelegate(gpuDelegate);
                    break;
                case CPU:
                    break;
            }


            // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
            // finally, create a new interpreter object

            Trace.beginSection("initializing new classifier");
            INTERPRETER = new Interpreter(MODELBUFFER, tfliteOptions);
            LOGGER.i("+++ NEW, initialize(): successfully created new Interpreter object!");
            Trace.endSection();

            // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
            // prepare post-processing

            Trace.beginSection("inititializing postprocessing");

            // read type and shape of input tensors
            int imageTensorIndex = 0;
            int[] imageShape = INTERPRETER.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
            imageSizeY = imageShape[1];
            imageSizeX = imageShape[2];

            // read type and shape of output tensors
            DataType imageDataType = INTERPRETER.getInputTensor(imageTensorIndex).dataType();
            int probabilityTensorIndex = 0;
            int[] probabilityShape = INTERPRETER.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUMBER_OF_CLASSES}
            DataType probabilityDataType = INTERPRETER.getOutputTensor(probabilityTensorIndex).dataType();

            // create input tensor
            inputImageBuffer = new TensorImage(imageDataType);

            // create output tensor and its processor
            outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

            // create post-processor for the output probability
            probabilityProcessor = new TensorProcessor.Builder().add(modelConfig.getPostprocessNormalizeOp()).build();

            Trace.endSection();

            // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
            // done

            LOGGER.i("+++ NEW, initialize(): successfully created a TensorFlow Lite Image Classifier with model '" + modelConfig.getName() + "'");

        }}).start(); // END of new thread

    } // END OF INITIALIZE - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    /** Runs inference and returns the classification results.
     *
     * @param: bitmap:            a RGB bitmap
     *
     * */
    public static List<Recognition> classify2(final Bitmap bitmap) throws InterruptedException {

        Trace.beginSection("NewSingletonClassifier classify()");

        if ((INTERPRETER == null) || (inputImageBuffer == null) || (outputProbabilityBuffer == null) ){
            LOGGER.i("+++ NEW, classify2(): (tflite is NULL) || (inputImageBuffer is NULL) || (outputProbabilityBuffer is NULL)");
            final ArrayList<Recognition> recognitions = new ArrayList<>();
            return recognitions;
        }

        LOGGER.i("+++ NEW: classify2()    with model '" + modelConfig.getName() + "'");

        // load image into new TensorImage
        Trace.beginSection("loadImage");
        long startTimeForLoadImage = SystemClock.uptimeMillis();

        inputImageBuffer = loadImage(bitmap);

        long endTimeForLoadImage = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Time cost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));


        // run model inference on image
        Trace.beginSection("runInference");
        long startTimeForReference = SystemClock.uptimeMillis();

        INTERPRETER.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

        long endTimeForReference = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Time cost to run model inference: " + (endTimeForReference - startTimeForReference));


        // Gets the map of label and probability.
        Map<String, Float> labeledProbability = new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                .getMapWithFloatValue();


        Trace.endSection(); // end 'NewSingletonClassifier classify()' section

        // Gets top-k results.
        return getTopKProbability(labeledProbability);
    }



    /** Get the top-k results of the passed on list with all class scores */
    private static List<Recognition> getTopKProbability(Map<String, Float> labelProb) {
        // Find the best classifications.
        PriorityQueue<Recognition> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (Map.Entry<String, Float> entry : labelProb.entrySet()) {
            pq.add(new Recognition("" + entry.getKey(), entry.getKey(), entry.getValue(), null));
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }



    /** Loads input image-bitmap, and applies preprocessing.
     *
     * 'ImageProcessor' is a helper feature of the TF-Lite-Package */
    private static TensorImage loadImage(final Bitmap bitmap) {

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







    // ---------------------------------------------------------------------------------------------
    // HELPERS: load preferences

    // TODO: source out to static class

    private static int getThreadsFromPrefs() {

        int r = 1;

        // load number of threads from SharedPreferences
        int s = prefs.getInt("threads", 0);

        // update if within accepted range
        if ((s > 0) && (s <= 15)) {
            r = s;
        }

        return r;
    }

    private static ProcessingUnit getProcessingUnitFromPrefs(){

        ProcessingUnit r = ProcessingUnit.CPU;

        // load processing unit from SharedPreferences
        int s = prefs.getInt("processing_unit", 0);

        if(s == ProcessingUnit.GPU.hashCode()){
            r = ProcessingUnit.GPU;
        }
        if(s == ProcessingUnit.NNAPI.hashCode()){
            r = ProcessingUnit.NNAPI;
        }

        // we use CPU as default
        return r;

    }


    private static ModelConfig getModelFromPrefs(){

        // IDEA
        //  - get Id from SharedPreferences
        //  - iterate over list of models and check
        //  - if there is a ModelConfig that matches the specified id: return this item if
        //  - otherwise: return default "Float MobileNet V1"

        int id = prefs.getInt("model", 0);

        for (int i = 0; i < MODEL_LIST.size(); i++){

            ModelConfig m = MODEL_LIST.get(i);
//            LOGGER.i("Model #" + i + ": " + m.getName() );

            if(m.getId() == id) {
                return m;
            }

        }

        // if we get this far, there is no matching model
        return new ModelConfig();

    }

}