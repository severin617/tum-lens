package com.maxjokel.lens;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
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

import helpers.Logger;



interface ClassifierEvents {
    void onClassifierConfigChanged(Activity activity);
}



// TODO     IMPORTANT FINDING [24.09.2020, 00:55]
// TODO
// TODO         please do not use filenames with multiple '.' in them; those files will get
// TODO         compressed, what will cause a 'file not found' exception that crashes the app :(
// TODO
// TODO
// TODO     EXAMPLE:
// TODO         bad:  "mobilenet_v1.0_224.tflite"
// TODO         good: "mobilenet_v1.tflite"
// TODO
// TODO     YET I DO NOT UNDERSTAND HOW THIS WORKS WHEN USING PROPER CLASSES...


//  NOTE THAT THIS IS ORIGINALLY BASED ON:    https://github.com/tensorflow/examples/tree/master/lite/examples/image_classification/android



public class Classifier {
//public class Classifier implements ClassifierEvents { // NOPE!!! DOES NOT WORK


    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // SharedPreferences

    private SharedPreferences prefs;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    /** Number of results to show in the UI. */
    private static final int MAX_RESULTS = 5;

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected Interpreter tflite;

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** Labels corresponding to the output of the vision model. */
    private List<String> labels;

    /** Image size along the x axis. */
    private final int imageSizeX;

    /** Image size along the y axis. */
    private final int imageSizeY;

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
    private TensorOperator getPreprocessNormalizeOp(){ return modelConfig.getPreprocessNormalizeOp(); }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // constructor
    // Activity argument is required for reading from '/assets'
    // this method is adapted from the official TF-Lite example
    protected Classifier(Activity activity) throws IOException {

        // reset, just to make sure
        close();

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // load sharedPreferences object
        prefs = activity.getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // load saved 'number of threads' from SharedPreferences
        int saved_threadNumber = getThreadsFromPrefs();

        // load saved 'ProcessingUnit' from SharedPreferences
        ProcessingUnit saved_processingUnit = getProcessingUnitFromPrefs();

        // load saved model from SharedPreferences
        modelConfig = getModelFromPrefs();


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // now use this config to set up a new classifier

        // load .tflite file
        tfliteModel = FileUtil.loadMappedFile(activity, modelConfig.getModelFilename());

        // load labels as list
        labels = FileUtil.loadLabels(activity, modelConfig.getLabelFilename());

        // set number of threads
        tfliteOptions.setNumThreads(saved_threadNumber);

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
        // finally, create a new interpreter

        tflite = new Interpreter(tfliteModel, tfliteOptions);


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // prepare post-processing

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

        LOGGER.i("Created a TensorFlow Lite Image Classifier.");
    }












    /** Runs inference and returns the classification results.
     *
     * @param: bitmap:            die zuvor aus YUV zu RGB konvertierte Bitmap
     * @param: sensorOrientation: 0, weil warum nicht...
     *
     * */
//    public List<Classifier.Recognition> recognizeImage(final Bitmap bitmap, int sensorOrientation) {
    public List<Classifier.Recognition> recognizeImage(final Bitmap bitmap) {

        // Logs this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("loadImage");
        long startTimeForLoadImage = SystemClock.uptimeMillis();

        // ZUR INFO: inputImageBuffer = new TensorImage(imageDataType);
//        inputImageBuffer = loadImage(bitmap, sensorOrientation);
        inputImageBuffer = loadImage(bitmap);

        long endTimeForLoadImage = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));


        Trace.beginSection("runInference");
        long startTimeForReference = SystemClock.uptimeMillis();

        // run inference
        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

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
    }

    /** Closes the interpreter and model to release resources. */
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
        if (nnApiDelegate != null) {
            nnApiDelegate.close();
            nnApiDelegate = null;
        }
        tfliteModel = null;
    }


    /** Loads input image-bitmap, and applies preprocessing.
     *
     * 'ImageProcessor' is a helper feature of the TF-Lite-Package */
//    private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
    private TensorImage loadImage(final Bitmap bitmap) {

        // load bitmap into TensorImage
        inputImageBuffer.load(bitmap);

        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
//        int numRotation = sensorOrientation / 90;

        // create new TF-Lite ImageProcessor to convert from Bitmap to TensorImage
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
//                        .add(new Rot90Op(numRotation))
                        .add(getPreprocessNormalizeOp())
                        .build();

        return imageProcessor.process(inputImageBuffer);
    }






    /** Gets the top-k results. */
    private static List<Classifier.Recognition> getTopKProbability(Map<String, Float> labelProb) {
        // Find the best classifications.
        PriorityQueue<Classifier.Recognition> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<Classifier.Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (Map.Entry<String, Float> entry : labelProb.entrySet()) {
            pq.add(new Classifier.Recognition("" + entry.getKey(), entry.getKey(), entry.getValue(), null));
        }

        final ArrayList<Classifier.Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }




    // TODO - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // TODO: Recognition

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
    // HELPERS

    private int getThreadsFromPrefs() {

        int r = 1;

        // load number of threads from SharedPreferences
        int s = prefs.getInt("threads", 0);

        // update if within accepted range
        if ((s > 0) && (s <= 15)) {
            r = s;
        }

        return r;
    }

    private ProcessingUnit getProcessingUnitFromPrefs(){

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


    private ModelConfig getModelFromPrefs(){

        // IDEA
        //  - get Id from SharedPreferences
        //  - iterate over list of models and check
        //  - if there is a ModelConfig that matches the specified id: return this item if
        //  - otherwise: return default "Float MobileNet V1"

        int id = prefs.getInt("model", 0); // TODO

        for (int i = 0; i < MODEL_LIST.size(); i++){

            ModelConfig m = MODEL_LIST.get(i);

            if(m.getId() == id) {
                return m;
            }

        }

        // if we get this far, there is no matching model
        return new ModelConfig();

    }


}
