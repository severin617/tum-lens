package tflite;

import android.app.Activity;

import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;

import java.io.File;
import java.io.IOException;

/** This TensorFlow Lite classifier works with the Inception Net V1 model. */
public class Classifier_Inception_v1_quant extends Classifier {


    // ANALOG
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;

    // brauchen wir das überhaupt?!?!?!
    /** Quantized MobileNet requires additional dequantization to the output probability. */
    private static final float PROBABILITY_MEAN = 0.0f;
    private static final float PROBABILITY_STD = 255.0f;

    // in der Beschreibung steht nichts über etwaiges De-Quantifizieren
    /**
     * Float model does not need dequantization in the post-processing. Setting mean and std as 0.0f
     * and 1.0f, repectively, to bypass the normalization.
     */
//    private static final float PROBABILITY_MEAN = 0.0f;
//    private static final float PROBABILITY_STD = 1.0f;



    public Classifier_Inception_v1_quant(Activity activity, Device device, int numThreads)
            throws IOException {
        super(activity, device, numThreads);
    }

    @Override
    protected String getModelPath() {
        return "inception_v1_quant_1_metadata_1.tflite";
    }

    @Override
    protected String getLabelPath() {
        return "labels.txt";
    }

    @Override
    protected TensorOperator getPreprocessNormalizeOp() {
        return new NormalizeOp(IMAGE_MEAN, IMAGE_STD);
    }

    @Override
    protected TensorOperator getPostprocessNormalizeOp() {
        return new NormalizeOp(PROBABILITY_MEAN, PROBABILITY_STD);
    }




//
//    // SRC: https://tfhub.dev/tensorflow/lite-model/inception_v1_quant/1/metadata/1
//    File tfliteModel = "***.tflite";
//    tflite = new Interpreter(tfliteModel);  // Load model.
//
//    final int IMAGE_SIZE_X = 224;
//    final int IMAGE_SIZE_Y = 224;
//    final int DIM_BATCH_SIZE = 1;
//    final int DIM_PIXEL_SIZE = 3;
//    final int NUM_BYTES_PER_CHANNEL = 4;  // Quantized model is 1
//    final int NUM_CLASS = 1001;
//
//    // The example uses Bitmap ARGB_8888 format.
//    Bitmap bitmap = ...;
//
//    int[] intValues = new int[IMAGE_SIZE_X * IMAGE_SIZE_Y];
//        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
//
//    ByteBuffer imgData =
//            ByteBuffer.allocateDirect(
//                    DIM_BATCH_SIZE
//                            * IMAGE_SIZE_X
//                            * IMAGE_SIZE_Y
//                            * DIM_PIXEL_SIZE
//                            * NUM_BYTES_PER_CHANNEL);
//        imgData.rewind();
//
//    // Float model.
//    int pixel = 0;
//        for (int i = 0; i < IMAGE_SIZE_X; ++i) {
//        for (int j = 0; j < IMAGE_SIZE_Y; ++j) {
//            int pixelValue = intValues[pixel++];
//            imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//            imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//            imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//        }
//    }
//
//    // Quantized model.
//    int pixel = 0;
//        for (int i = 0; i < IMAGE_SIZE_X; ++i) {
//        for (int j = 0; j < IMAGE_SIZE_Y; ++j) {
//            imgData.put((byte) ((pixelValue >> 16) & 0xFF));
//            imgData.put((byte) ((pixelValue >> 8) & 0xFF));
//            imgData.put((byte) (pixelValue & 0xFF));
//        }
//    }
//
//    // Output label probabilities.
//    float[][] labelProbArray = new float[1][NUM_CLASS];
//
//// Run the model.
//        tflite.run(imgData, labelProbArray);

}




