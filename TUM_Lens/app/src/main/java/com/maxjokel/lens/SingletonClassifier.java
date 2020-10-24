package com.maxjokel.lens;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Trace;
import android.widget.LinearLayout;

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


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    24.10.2020

    SINGLETON pattern for creating a 'single source of truth'

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */



interface ReinitializationListener{
    public void onClassifierReinitialized();
}



//public class SingletonClassifier {
public class SingletonClassifier implements ClassifierEvents {


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // set up new LOGGER

    private static final Logger LOGGER = new Logger();


// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
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
//    private Interpreter.Options tfliteOptions = new Interpreter.Options();
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
    // set up CountDownLatch for 'await' behaviour when classifier is being initialized


    private CountDownLatch countDownLatch = new CountDownLatch(1);

    private CountDownLatch latchThatBlocksInitIfClassificationIsRunning = new CountDownLatch(1);


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    private boolean isBlocked = false;

    public boolean getIsBlocked(){ return isBlocked; }


// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // must be set to static!!
    private static List<ReinitializationListener> listeners = new ArrayList<ReinitializationListener>();

    public static void addListener(ReinitializationListener listener) {
        listeners.add(listener);
    }

    private static void notifyListeners() {
        for (ReinitializationListener listener : listeners) {
            listener.onClassifierReinitialized();
        }
    }

// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -









    // instance
//    private static final SingletonClassifier instance = new SingletonClassifier();
    private static SingletonClassifier instance = new SingletonClassifier();
    // darf nicht 'final' sein!!


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // private constructor that can't be accessed from outside
    private SingletonClassifier(){

        latchThatBlocksInitIfClassificationIsRunning.countDown();
        try {
            Trace.beginSection("private SingletonClassifier()");
            initialize();
            Trace.endSection();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    // public "constructor" that returns the instance
    public static SingletonClassifier getInstance() {
        return instance;
    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    @Override
    public void onClassifierConfigChanged(Activity activity) throws IOException {

//        LOGGER.i("SingletonClassifier was notified about configuration change; calling initialize()...");
        LOGGER.i("+++ SingletonClassifier notified about configuration change +++");

        // set new CountDownLatch
        countDownLatch = new CountDownLatch(1);

        // call initialize()
        Trace.beginSection("onClassifierConfigChanged: instance = new SingletonClassifier();");
        instance = new SingletonClassifier();
        Trace.endSection();
    }


// -------------------------------------------------------------------------------------------------










    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // initialize() method
    //
    // load preferences and set up classifier accordingly
    //
    private void initialize() throws IOException, InterruptedException {

        latchThatBlocksInitIfClassificationIsRunning.await();

        isBlocked = true;

        // source this out to new thread in order to reduce load on main-thread
//        new Thread( new Runnable() { @Override public void run() {

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

        LOGGER.i("??? loaded prefs");

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
            LOGGER.i("??? trying to load tflitemodel");
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
            LOGGER.i("??? trying to load label");
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



        LOGGER.i("is tfliteModel == null??? " + (tfliteModel == null) + "");
        LOGGER.i("is tflite == null??? " + (tflite == null) + "");


        // classifier is now initialized, reset counter to allow 'recognizeImage()'
        countDownLatch.countDown();


        LOGGER.i("+++ SingletonClassifier: notifying listeners!! +++");
        notifyListeners();


//        }}).start(); // END of new thread

        isBlocked = false;

    } // END OF INITIALIZE - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -



    /** Runs inference and returns the classification results.
     *
     * @param: bitmap:            a RGB bitmap
     *
     * */
    /*public static List<StaticClassifier.Recognition> recognizeImage(final Bitmap bitmap) throws InterruptedException {


        // wait until classifier is initialized
        countDownLatch.await();

        LOGGER.i("StaticClassifier: Starting to classify with model '" + modelConfig.getName() + "'");


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
//        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

        try{
            tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());
        } catch (Exception e){
            LOGGER.v("### FAILED TO RUN CLASSIFICATION ###");
        }


        long endTimeForReference = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Timecost to run model inference: " + (endTimeForReference - startTimeForReference));


        // Gets the map of label and probability.
        Map<String, Float> labeledProbability =
                new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue();
        Trace.endSection();

        // Gets top-k results.
        return getTopKProbability(labeledProbability);
    }*/


    public int recognizeImage2(final Bitmap bitmap) throws InterruptedException {


        latchThatBlocksInitIfClassificationIsRunning = new CountDownLatch(1);

        // wait until classifier is initialized
        countDownLatch.await();

        LOGGER.i("+++ SingletonClassifier: Starting to classify with model '" + modelConfig.getName() + "' +++");


        if (isBlocked){
            LOGGER.i("#### #### SingletonClassifier: isBlocked == TRUE #### #### ");
            return 55;
        }

//        final ArrayList<Recognition> recognitions = new ArrayList<>();
//
//        for (int i = 0; i<5; i++){
//            recognitions.add(new Recognition(""+i, ""+i, 0.0f, new RectF(0,0,0,0)));
//        }


        if (tflite == null){
            LOGGER.i("#### #### SingletonClassifier: tflite is NULL #### #### ");
            return 999;
        }
        if (inputImageBuffer == null){
            LOGGER.i("#### #### SingletonClassifier: inputImageBuffer is NULL #### #### ");
            return 888;
        }
        if (outputProbabilityBuffer == null){
            LOGGER.i("#### #### SingletonClassifier: outputProbabilityBuffer is NULL #### #### ");
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


        latchThatBlocksInitIfClassificationIsRunning.countDown();

        return 1;
    }





    /** Closes the interpreter and model to release resources. */
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
//        if (gpuDelegate != null) {
//            gpuDelegate.close();
//            gpuDelegate = null;
//        }
//        if (nnApiDelegate != null) {
//            nnApiDelegate.close();
//            nnApiDelegate = null;
//        }

        tfliteModel.clear();
        tfliteModel = null;
    }


    /** Loads input image-bitmap, and applies preprocessing.
     *
     * 'ImageProcessor' is a helper feature of the TF-Lite-Package */
//    private static TensorImage loadImage(final Bitmap bitmap) {
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






    /** Gets the top-k results. */
    private static List<SingletonClassifier.Recognition> getTopKProbability(Map<String, Float> labelProb) {
        // Find the best classifications.
        PriorityQueue<SingletonClassifier.Recognition> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<SingletonClassifier.Recognition>() {
                            @Override
                            public int compare(SingletonClassifier.Recognition lhs, SingletonClassifier.Recognition rhs) {
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (Map.Entry<String, Float> entry : labelProb.entrySet()) {
            pq.add(new SingletonClassifier.Recognition("" + entry.getKey(), entry.getKey(), entry.getValue(), null));
        }

        final ArrayList<SingletonClassifier.Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }




    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // The following methods are related to 'Recognition'

    /** An immutable result returned by a Classifier describing what was recognized. */
    public static class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /** Display name for the recognition. */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /** Optional location within the source image for the location of the recognized object. */
        private RectF location;

        public Recognition(
                final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }



    // ---------------------------------------------------------------------------------------------
    // HELPERS: load preferences

//    private int getThreadsFromPrefs() {
//
//        int r = 1;
//
//        // load number of threads from SharedPreferences
//        int s = prefs.getInt("threads", 0);
//
//        // update if within accepted range
//        if ((s > 0) && (s <= 15)) {
//            r = s;
//        }
//
//        return r;
//    }
//
//    private ProcessingUnit getProcessingUnitFromPrefs(){
//
//        ProcessingUnit r = ProcessingUnit.CPU;
//
//        // load processing unit from SharedPreferences
//        int s = prefs.getInt("processing_unit", 0);
//
//        if(s == ProcessingUnit.GPU.hashCode()){
//            r = ProcessingUnit.GPU;
//        }
//        if(s == ProcessingUnit.NNAPI.hashCode()){
//            r = ProcessingUnit.NNAPI;
//        }
//
//        // we use CPU as default
//        return r;
//
//    }
//
//
//    private ModelConfig getModelFromPrefs(){
//
//        // IDEA
//        //  - get Id from SharedPreferences
//        //  - iterate over list of models and check
//        //  - if there is a ModelConfig that matches the specified id: return this item if
//        //  - otherwise: return default "Float MobileNet V1"
//
//        int id = prefs.getInt("model", 0); // TODO
//
//        for (int i = 0; i < MODEL_LIST.size(); i++){
//
//            ModelConfig m = MODEL_LIST.get(i);
//
//            if(m.getId() == id) {
//                return m;
//            }
//
//        }
//
//        // if we get this far, there is no matching model
//        return new ModelConfig();
//
//    }


}


// -------------------------------------------------------------------------------------------------
