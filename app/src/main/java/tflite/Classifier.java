/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package tflite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Trace;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import helpers.Logger;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** A classifier specialized to label images using TensorFlow Lite. */
public abstract class Classifier {
  private static final Logger LOGGER = new Logger();

  /** The model type used for classification. */
  public enum Model {
    FLOAT_MOBILENET,
    QUANTIZED_MOBILENET,
    FLOAT_EFFICIENTNET,
    QUANTIZED_EFFICIENTNET,
    INCEPTION_V1

    // 30.08.2020
    , INCEPTION_V1_selfConverted,

    // 20.09.2020
    MIRCO_MOBILENET_V3
  }

  /** The runtime device type used for executing classification. */
  public enum Device {
    CPU,
    NNAPI,
    GPU
  }

  /** Number of results to show in the UI. */
  private static final int MAX_RESULTS = 5;

  /** The loaded TensorFlow Lite model. */
  private MappedByteBuffer tfliteModel;

  /** Image size along the x axis. */
  private final int imageSizeX;

  /** Image size along the y axis. */
  private final int imageSizeY;

  /** Optional GPU delegate for accleration. */
  private GpuDelegate gpuDelegate = null;

  /** Optional NNAPI delegate for accleration. */
  private NnApiDelegate nnApiDelegate = null;

  /** An instance of the driver class to run model inference with Tensorflow Lite. */
  protected Interpreter tflite;

  /** Options for configuring the Interpreter. */
  private final Interpreter.Options tfliteOptions = new Interpreter.Options();

  /** Labels corresponding to the output of the vision model. */
  private List<String> labels;

  /** Input image TensorBuffer. */
  private TensorImage inputImageBuffer;

  /** Output probability TensorBuffer. */
  private final TensorBuffer outputProbabilityBuffer;

  /** Processer to apply post processing of the output probability. */
  private final TensorProcessor probabilityProcessor;

  /**
   * Creates a classifier with the provided configuration.
   *
   * @param activity The current Activity.
   * @param model The model to use for classification.
   * @param device The device to use for classification.
   * @param numThreads The number of threads to use for classification.
   * @return A classifier with the desired configuration.
   */
  public static Classifier create(Activity activity, Model model, Device device, int numThreads)
      throws IOException {
    if (model == Model.QUANTIZED_MOBILENET) {
      return new ClassifierQuantizedMobileNet(activity, device, numThreads);
    } else if (model == Model.FLOAT_MOBILENET) {
      return new ClassifierFloatMobileNet(activity, device, numThreads);
    } else if (model == Model.FLOAT_EFFICIENTNET) {
      return new ClassifierFloatEfficientNet(activity, device, numThreads);
    } else if (model == Model.QUANTIZED_EFFICIENTNET) {
      return new ClassifierQuantizedEfficientNet(activity, device, numThreads);

      // Inception Net V1
    } else if (model == Model.INCEPTION_V1) {
      return new Classifier_Inception_v1_quant(activity, device, numThreads);
    }

    // 30.08.2020
     else if (model == Model.INCEPTION_V1_selfConverted) {
       return new Pfusch_InceptionV1(activity, device, numThreads);
     }

     // 20.09.2020
    else if (model == Model.MIRCO_MOBILENET_V3) {
      return new Mirco_MobileNetV3(activity, device, numThreads);
    }



    else {
      throw new UnsupportedOperationException();
    }
  }

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

  /** Initializes a {@code Classifier}. */
  protected Classifier(Activity activity, Device device, int numThreads) throws IOException {
    tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());
    switch (device) {
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
    tfliteOptions.setNumThreads(numThreads);
    tflite = new Interpreter(tfliteModel, tfliteOptions);

    // Loads labels out from the label file.
    labels = FileUtil.loadLabels(activity, getLabelPath());

    // Reads type and shape of input and output tensors, respectively.
    int imageTensorIndex = 0;
    int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
    imageSizeY = imageShape[1];
    imageSizeX = imageShape[2];
    DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
    int probabilityTensorIndex = 0;
    int[] probabilityShape =
        tflite.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUM_CLASSES}
    DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();

    // Creates the input tensor.
    inputImageBuffer = new TensorImage(imageDataType);

    // Creates the output tensor and its processor.
    outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

    // Creates the post processor for the output probability.
    probabilityProcessor = new TensorProcessor.Builder().add(getPostprocessNormalizeOp()).build();

    LOGGER.d("Created a Tensorflow Lite Image Classifier.");
  }

  /** Runs inference and returns the classification results.
   *
   * @param: bitmap:            die zuvor aus YUV zu RGB konvertierte Bitmap
   * @param: sensorOrientation: 0, weil warum nicht...
   *
   * */
  public List<Recognition> recognizeImage(final Bitmap bitmap, int sensorOrientation) {
    // Logs this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("loadImage");
    long startTimeForLoadImage = SystemClock.uptimeMillis();

    // ZUR INFO: inputImageBuffer = new TensorImage(imageDataType);
    inputImageBuffer = loadImage(bitmap, sensorOrientation);

//    System.out.println("-> bitmap           Width and Height: " + bitmap.getWidth() + "px x " + bitmap.getHeight() + "px; has " + bitmap.getByteCount() + " bytes. has alpha: " + bitmap.hasAlpha());
//    System.out.println("-> inputImageBuffer Width and Height: " + inputImageBuffer.getWidth() + "px x " + inputImageBuffer.getHeight() + "px; data type: " + inputImageBuffer.getDataType());


    long endTimeForLoadImage = SystemClock.uptimeMillis();
    Trace.endSection();
    LOGGER.v("Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));
//    System.out.println("Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));
// BIS HIER HER KOMMEN WIR SCHON MAL


    // Runs the inference call.
    Trace.beginSection("runInference");
    long startTimeForReference = SystemClock.uptimeMillis();

//    this one works with MobileNet, etc.
    tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

// 25. Juli: attempt to make InceptionNet work
// "Cannot convert between a TensorFlowLite buffer with 150.528 [=224x224*3] bytes and a Java Buffer with 602.112 [448x448*3] bytes."
//
//   602.112 = 448x448 x 3
//    also werden die Dimensionen verdoppelt?!

// -> Java Buffer ist scheinbar zu groß...


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
  private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {

    // Loads bitmap into a TensorImage
    inputImageBuffer.load(bitmap);

//    int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
    int cropSize = getInputDimX(); // alternative implementation

    int numRotation = sensorOrientation / 90;

    // create new TF-Lite ImageProcessor to convert from Bitmap to TensorImage
    ImageProcessor imageProcessor =
        new ImageProcessor.Builder()
                // adapted previous implementation:
                // size of image is now based on the 'model-input-dimensions'
            .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
//          .add(new ResizeOp(imageSizeX, imageSizeY, ResizeMethod.NEAREST_NEIGHBOR))
            .add(new ResizeOp(getInputDimX(), getInputDimY(), ResizeMethod.NEAREST_NEIGHBOR))
            .add(new Rot90Op(numRotation))
            .add(getPreprocessNormalizeOp())
            .build();

    return imageProcessor.process(inputImageBuffer);
  }

  /** Gets the top-k results. */
  private static List<Recognition> getTopKProbability(Map<String, Float> labelProb) {
    // Find the best classifications.
    PriorityQueue<Recognition> pq =
        new PriorityQueue<>(
            MAX_RESULTS,
            new Comparator<Recognition>() {
              @Override
              public int compare(Recognition lhs, Recognition rhs) {
                // Intentionally reversed to put high confidence at the head of the queue.
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

  /** Gets the name of the model file stored in Assets. */
  protected abstract String getModelPath();

  /** Gets the name of the label file stored in Assets. */
  protected abstract String getLabelPath();

  /** Gets the TensorOperator to nomalize the input image in preprocessing. */
  protected abstract TensorOperator getPreprocessNormalizeOp();

  /**
   * Gets the TensorOperator to dequantize the output probability in post processing.
   *
   * <p>For quantized model, we need de-quantize the prediction with NormalizeOp (as they are all
   * essentially linear transformation). For float model, de-quantize is not required. But to
   * uniform the API, de-quantize is added to float model too. Mean and std are set to 0.0f and
   * 1.0f, respectively.
   */
  protected abstract TensorOperator getPostprocessNormalizeOp();

  /** Get the image size along the x axis. */
  public int getImageSizeX() {
    return imageSizeX;
  }

  /** Get the image size along the y axis. */
  public int getImageSizeY() {
    return imageSizeY;
  }

  // NEU
  // 26. JULI
  // muss public sein, weil wir sonst nicht aus den anderen Klassen zugreifen können
//  protected
  public abstract int getInputDimX();
//  protected
  public abstract int getInputDimY();
}
