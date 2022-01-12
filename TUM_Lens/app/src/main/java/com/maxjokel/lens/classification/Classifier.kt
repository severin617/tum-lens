package com.maxjokel.lens.classification

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.os.Trace
import com.maxjokel.lens.helpers.App.Companion.context
import com.maxjokel.lens.helpers.Logger
import com.maxjokel.lens.helpers.ModelConfig
import com.maxjokel.lens.helpers.ProcessingUnit
import com.maxjokel.lens.helpers.Recognition
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.CountDownLatch

/**
 * This 'classifier' implementation is based around the idea that a single "classifier instance"
 * is sufficient for both, ViewFinder and CameraRoll activity;
 *
 * By exposing the 'classifier' as an object it can be accessed very efficiently from both
 * activities, resulting in a much improved user experience;
 */
object Classifier {

    private val LOGGER = Logger()

    /** SharedPreferences for user configuration  */
    private var prefs: SharedPreferences? = null

    /** Number of results to show in the UI.  */
    private const val MAX_RESULTS = 5

    /** The loaded TensorFlow Lite model.  */
    private var MODELBUFFER: MappedByteBuffer? = null

    /** An instance of the driver class to run model inference with Tensorflow Lite.  */
    private var INTERPRETER: Interpreter? = null

    /** Options for configuring the Interpreter.  */
    private var tfliteOptions: Interpreter.Options? = null

    /** Labels corresponding to the output of the vision model.  */
    private var labels: List<String>? = null

    /** Image size along the x axis.  */
    private var imageSizeX = 0

    /** Image size along the y axis.  */
    private var imageSizeY = 0

    /** Input image TensorBuffer.  */
    private var inputImageBuffer: TensorImage? = null

    /** Output probability TensorBuffer.  */
    private var outputProbBuffer: TensorBuffer? = null

    /** Processer to apply post processing of the output probability.  */
    private var probProcessor: TensorProcessor? = null

    /** Optional GPU delegate for accleration.  */
    private var gpuDelegate: GpuDelegate? = null

    /** Optional NNAPI delegate for accleration.  */
    private var nnApiDelegate: NnApiDelegate? = null

    // everything related to 'ListSingleton'
    private val MODEL_LIST = ListSingleton.modelConfigs

    // set up initial 'ModelConfig' object and related helper functions
    private var modelConfig: ModelConfig? = null
    private val preprocessNormalizeOp: TensorOperator
        get() = modelConfig!!.preprocessNormalizeOp

    // set up CountDownLatches that help us to manage the control flow
    private var latchThatBlocksINITIALIZATION = CountDownLatch(0)
    private var latchThatBlocksCLASSIFICATiON: CountDownLatch? = null


    /** Wrapper method for image classification
     *
     * waits for any ongoing initializations to finish;
     * sets a new Latch to block any other initializations and runs inference;
     * returns a list of the top-n Recognition objects;
     *
     * @param: bitmap:      a RGB bitmap
     *
     */
    fun recognizeImage(bitmap: Bitmap?): List<Recognition> {
        return try {
            latchThatBlocksINITIALIZATION.await() // wait for initialization to finish
            latchThatBlocksINITIALIZATION = CountDownLatch(1) // block until classification is done
            if (bitmap == null) throw Exception()
            val recognitions = classify(bitmap)
            latchThatBlocksINITIALIZATION.countDown()
            recognitions
        } catch (e: Exception) {
            e.printStackTrace()
            ArrayList()
        }
    }

    /** Memory-map the model file in Assets.  */
    @Throws(IOException::class)
    private fun loadModelFile(modelFilename: String): MappedByteBuffer {
            val root : String = Environment.getExternalStorageDirectory().absolutePath + "/models"
            val path = File(root)
            val exactPath = File("$path/$modelFilename")

            val inputStream = FileInputStream(exactPath)
            val fileChannel = inputStream.channel
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, exactPath.length())
    }

    /** This method initializes the global 'tflite' and 'tfliteModel' objects with regards to the
     *  the user's configuration;
     *  The method utilizes a separate thread in order to reduce load on the main-thread */
    fun initialize() {
        try {
            // wait for a running classification to finnish
            latchThatBlocksINITIALIZATION.await()

            // block any classification until the initialization is done
            latchThatBlocksCLASSIFICATiON = CountDownLatch(1)
            LOGGER.i("+++ NEW: latchThatBlocksCLASSIFICATiON = new CountDownLatch(1); in constructor for NewSingletonClassifier() -> setup init")

            // initialize classifier object, uses extra thread!!
            LOGGER.i("+++ NEW, initialize():  starting new thread")

            // utilize new thread
            Thread { // reset Interpreter and MappedByteBuffer
                INTERPRETER?.close()
                INTERPRETER = null
                LOGGER.i("+++ NEW, initialize(): reset Interpreter")
                MODELBUFFER?.clear()
                MODELBUFFER = null
                LOGGER.i("+++ NEW, initialize(): reset MappedByteBuffer")

                prefs = context!!.getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)
                LOGGER.i("+++ NEW, initialize(): successfully loaded sharedPreferences")
                val savedThreadNumber = threadsFromPrefs
                LOGGER.i("+++ NEW, initialize(): successfully loaded saved_threadNumber")
                val savedProcessingUnit = processingUnitFromPrefs
                LOGGER.i("+++ NEW, initialize(): successfully loaded saved_processingUnit")
                modelConfig = modelFromPrefs
                LOGGER.i("+++ NEW, initialize(): successfully loaded modelConfig")

                // load .tflite file from '/assets'
                Trace.beginSection("loading file into MODELBUFFER")
                try {
                    MODELBUFFER = if (modelConfig!!.modelFilename!! == "mobilenet_v1_224_quant.tflite") {
                        FileUtil.loadMappedFile(context!!, modelConfig!!.modelFilename!!)
                    } else {
                        loadModelFile(modelConfig!!.modelFilename!!)
                    }

                    LOGGER.i("+++ NEW, initialize(): successfully loaded tflite file into MODELBUFFER")
                } catch (e: IOException) {
                    LOGGER.i("### NEW, initialize(): FAILED to load tflite file into MODELBUFFER")
                    e.printStackTrace()
                }
                Trace.endSection()

                // load labels file from '/assets' as list
                Trace.beginSection("loading labels")
                try {
                    labels = FileUtil.loadLabels(context!!, modelConfig!!.labelFilename!!)
                    LOGGER.i("+++ NEW, initialize(): successfully loaded label file")
                } catch (e: IOException) {
                    LOGGER.i("### NEW, initialize(): FAILED to load labels")
                    e.printStackTrace()
                }
                Trace.endSection()

                tfliteOptions = Interpreter.Options()
                tfliteOptions!!.setNumThreads(savedThreadNumber)
                LOGGER.i("+++ NEW, initialize(): successfully set number of threads")

                when (savedProcessingUnit) {
                    ProcessingUnit.NNAPI -> {
                        nnApiDelegate = NnApiDelegate()
                        tfliteOptions!!.addDelegate(nnApiDelegate)
                    }
                    ProcessingUnit.GPU -> {
                        gpuDelegate = GpuDelegate()
                        tfliteOptions!!.addDelegate(gpuDelegate)
                    }
                    ProcessingUnit.CPU -> {
                    }
                }

                Trace.beginSection("initializing new classifier")
                INTERPRETER = Interpreter(MODELBUFFER!!, tfliteOptions)
                LOGGER.i("+++ NEW, initialize(): successfully created new Interpreter object!")
                Trace.endSection()

                Trace.beginSection("initializing postprocessing")

                // read type and shape of input tensors
                val imageTensorIndex = 0
                val imageShape = INTERPRETER!!.getInputTensor(imageTensorIndex).shape()
                imageSizeY = imageShape[1]
                imageSizeX = imageShape[2]

                // read type and shape of output tensors
                val imageDataType = INTERPRETER!!.getInputTensor(imageTensorIndex).dataType()
                val probTensorIndex = 0
                val probShape = INTERPRETER!!.getOutputTensor(probTensorIndex).shape()
                val probDataType = INTERPRETER!!.getOutputTensor(probTensorIndex).dataType()

                // create input tensor
                inputImageBuffer = TensorImage(imageDataType)

                // create output tensor and its processor
                outputProbBuffer = TensorBuffer.createFixedSize(probShape, probDataType)

                // create post-processor for the output probability
                probProcessor = TensorProcessor.Builder()
                    .add(modelConfig!!.postprocessNormalizeOp).build()
                Trace.endSection()

                LOGGER.i("+++ NEW, initialize(): successfully created a TensorFlow" +
                    " Lite Image Classifier with model '" + modelConfig!!.modelName + "'")
            }.start()

            latchThatBlocksCLASSIFICATiON!!.countDown()
            LOGGER.i("+++ NEW: latchThatBlocksCLASSIFICATiON.countdown(); in Classfier.initialize() -> init complete")
        } catch (e: InterruptedException) {
            LOGGER.i("+++ NEW: InterruptedException in NewSingletonClassifier()  -> latch")
            e.printStackTrace()
        }
    }

    /** Runs inference and returns the classification results.
     *
     * @param: bitmap:            a RGB bitmap
     *
     */
    @Throws(InterruptedException::class)
    fun classify(bitmap: Bitmap): List<Recognition> {
        Trace.beginSection("NewSingletonClassifier classify()")
        if (INTERPRETER == null || inputImageBuffer == null || outputProbBuffer == null) {
            LOGGER.i("### NEW, classify(): (tflite is NULL) || (inputImageBuffer is NULL) || (outputProbBuffer is NULL)")
            return ArrayList()
        }

        Trace.beginSection("loadImage")
        val loadImageStartTime = SystemClock.uptimeMillis()
        inputImageBuffer = loadImage(bitmap)
        val loadImageEndTime = SystemClock.uptimeMillis()
        Trace.endSection()
        LOGGER.v("Time cost to load the image: " + (loadImageEndTime - loadImageStartTime))

        // run model inference on image
        Trace.beginSection("runInference")
        val referenceStartTime = SystemClock.uptimeMillis()
        // FIXME: The following line can produce NPEs
        INTERPRETER!!.run(inputImageBuffer!!.buffer, outputProbBuffer!!.buffer.rewind())
        val referenceEndTime = SystemClock.uptimeMillis()
        Trace.endSection()
        LOGGER.v("Time cost to run model inference: " + (referenceEndTime - referenceStartTime))

        // Gets the map of label and probability.
        val labeledProb = TensorLabel(labels!!, probProcessor!!.process(outputProbBuffer))
            .mapWithFloatValue
        Trace.endSection()

        return getTopKProbability(labeledProb)
    }

    fun onConfigChanged() {
        LOGGER.i("+++ NEW: received 'onConfigChanged' event [FRAGMENT]")
        Trace.beginSection("+++ NEW: onClassifierConfigChanged")
        initialize()
        Trace.endSection()
        LOGGER.i("+++ NEW: successfully initialized new instance")
    }

    /** Get the top-k results of the passed on list with all class scores  */
    private fun getTopKProbability(labeledProb: Map<String, Float>): List<Recognition> {
        // Find the best classifications.
        val pq = PriorityQueue<Recognition>(MAX_RESULTS) {
                lhs, rhs -> (rhs.confidence!!).compareTo(lhs.confidence!!)
        }
        for ((key, value) in labeledProb) {
            pq.add(Recognition("" + key, key, value, null))
        }
        val recognitions = ArrayList<Recognition>()
        val numberOfResults = pq.size.coerceAtMost(MAX_RESULTS)
        repeat(numberOfResults) { recognitions.add(pq.poll()!!) }
        return recognitions
    }

    /** Loads input image-bitmap, and applies preprocessing.
     *
     * 'ImageProcessor' is a helper feature of the TF-Lite-Package  */
    private fun loadImage(bitmap: Bitmap): TensorImage {
        // load bitmap into TensorImage
        inputImageBuffer!!.load(bitmap)
        val cropSize = bitmap.width.coerceAtMost(bitmap.height)

        // create new TF-Lite ImageProcessor to convert from Bitmap to TensorImage
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(preprocessNormalizeOp)
            .build()
        return imageProcessor.process(inputImageBuffer)
    }

    private val threadsFromPrefs: Int
        get() {
            val s = prefs!!.getInt("threads", 1)
            return if (s in 1..15) { s } else { 1 }
        }

    private val processingUnitFromPrefs: ProcessingUnit
        get() {
            return when (prefs!!.getInt("processing_unit", 0)) {
                ProcessingUnit.GPU.hashCode() -> ProcessingUnit.GPU
                ProcessingUnit.NNAPI.hashCode() -> ProcessingUnit.NNAPI
                else -> ProcessingUnit.CPU
            }
        }


    private val modelFromPrefs: ModelConfig
        get() {
            val id = prefs!!.getInt("model", 0)
            for (modelConfig in MODEL_LIST) {
                if (modelConfig.modelId == id) {
                    return modelConfig
                }
            }
            return ModelConfig()
        }
}