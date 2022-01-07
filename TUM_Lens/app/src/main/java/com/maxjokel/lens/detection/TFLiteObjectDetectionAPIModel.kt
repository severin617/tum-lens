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
package com.maxjokel.lens.detection

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Environment
import android.os.Trace
import android.util.Log
import com.maxjokel.lens.helpers.App
import com.maxjokel.lens.helpers.Recognition
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.metadata.MetadataExtractor
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.*

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API: -
 * https://github.com/tensorflow/models/tree/master/research/object_detection where you can find the
 * training code.
 *
 *
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * -
 * https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/tf1_detection_zoo.md
 * -
 * https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/tf2_detection_zoo.md
 * -
 * https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
class TFLiteObjectDetectionAPIModel private constructor() : Detector {
    private var isModelQuantized = false

    // Config values.
    private var inputSize = 0

    // Pre-allocated buffers.
    private val labels: MutableList<String> = ArrayList()
    private lateinit var intValues: IntArray

    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private lateinit var outputLocations: Array<Array<FloatArray>>

    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private lateinit var outputClasses: Array<FloatArray>

    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private lateinit var outputScores: Array<FloatArray>

    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private lateinit var numDetections: FloatArray
    private var imgData: ByteBuffer? = null
    private var tfLiteModel: MappedByteBuffer? = null
    private var tfLiteOptions: Interpreter.Options? = null
    private var tfLite: Interpreter? = null

    private lateinit var prefs: SharedPreferences


    override fun recognizeImage(bitmap: Bitmap?): List<Recognition?> {
        // Log this method so that it can be analyzed with systrace.

        prefs = App.context!!.getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)
        val id = prefs.getInt("model_detection", 0)

        Trace.beginSection("recognizeImage")
        Trace.beginSection("preprocessBitmap")
        assert(bitmap != null)
        bitmap!!.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        imgData!!.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    // Quantized model
                    imgData!!.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData!!.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData!!.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData!!.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        Trace.endSection() // preprocessBitmap

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed")
        outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
        outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
        outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
        numDetections = FloatArray(1)
        val inputArray = arrayOf<Any?>(imgData)
        val outputMap: MutableMap<Int, Any> = HashMap()

        if (id == 0) {
            // detect.tflite
            Log.d("OutputMap", "DefafultModel")
            outputMap[0] = outputLocations
            outputMap[1] = outputClasses
            outputMap[2] = outputScores
            outputMap[3] = numDetections
        } else {
            // model_meta.tflite  the model that I am testing
            Log.d("OutputMap", "NewModel")
            outputMap[0] = outputScores
            outputMap[1] = outputLocations
            outputMap[2] = numDetections
            outputMap[3] = outputClasses
        }


        Trace.endSection()

        Log.d("inputSize", "input size is $inputSize")

        // Run the inference call.
        Trace.beginSection("run")
        tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap)
        Trace.endSection()

        // Show the best detections.
        // after scaling them back to the input size.
        // You need to use the number of detections from the output and not the NUM_DETECTONS variable
        // declared on top
        // because on some models, they don't always output the same total number of detections
        // For example, your model's NUM_DETECTIONS = 20, but sometimes it only outputs 16 predictions
        // If you don't use the output's numDetections, you'll get nonsensical data
        val numDetectionsOutput =
            NUM_DETECTIONS.coerceAtMost(numDetections[0].toInt()) // cast from float to integer
        val recognitions = ArrayList<Recognition?>(numDetectionsOutput)
        for (i in 0 until numDetectionsOutput) {
            val detection = RectF(
                outputLocations[0][i][1] * inputSize,
                outputLocations[0][i][0] * inputSize,
                outputLocations[0][i][3] * inputSize,
                outputLocations[0][i][2] * inputSize
            )
            recognitions.add(Recognition(
                "" + i, labels[outputClasses[0][i].toInt()], outputScores[0][i], detection
            ))
        }
        Trace.endSection() // "recognizeImage"
        Log.d("TFLiteObjectDetection", "finished the recognize image function")
        return recognitions
    }

    override fun enableStatLogging(logStats: Boolean) {}
    override val statString: String
        get() = ""

    override fun close() {
        if (tfLite != null) {
            tfLite!!.close()
            tfLite = null
        }
    }

    override fun setNumThreads(numThreads: Int) {
        if (tfLite != null) {
            tfLiteOptions!!.setNumThreads(numThreads)
            recreateInterpreter()
        }
    }

    override fun setUseNNAPI(isChecked: Boolean) {
        if (tfLite != null) {
            tfLiteOptions!!.setUseNNAPI(isChecked)
            recreateInterpreter()
        }
    }

    private fun recreateInterpreter() {
        tfLite!!.close()
        tfLite = Interpreter(tfLiteModel!!, tfLiteOptions)
    }

    companion object {
        private const val TAG = "TFLiteObjectDetectionAPIModelWithInterpreter"

        // Only return this many results.
        private const val NUM_DETECTIONS = 10

        // Float model
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f

//        private const val IMAGE_MEAN = 0.0f
//        private const val IMAGE_STD = 1.0f

        // Number of threads in the java app
        private const val NUM_THREADS = 4

        /** Memory-map the model file in Assets.  */
        @Throws(IOException::class)
        private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {

            if (modelFilename == "detect.tflite") {

                val fileDescriptor = assets.openFd(modelFilename)
                val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
            else {
                val root : String = Environment.getExternalStorageDirectory().absolutePath + "/models"
                val path = File(root)
                val exactPath = File("$path/$modelFilename")

                val inputStream = FileInputStream(exactPath)
                val fileChannel = inputStream.channel
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, exactPath.length())
            }
        }

        /**
         * Initializes a native TensorFlow session for classifying images.
         *
         * @param modelFilename The model file path relative to the assets folder
         * @param labelFilename The label file path relative to the assets folder
         * @param inputSize The size of image input
         * @param isQuantized Boolean representing model is quantized or not
         */
        @Throws(IOException::class)
        fun create(context: Context, modelFilename: String, labelFilename: String?, inputSize: Int,
                   isQuantized: Boolean): Detector {

            val d = TFLiteObjectDetectionAPIModel()
            val modelFile = loadModelFile(context.assets, modelFilename)
            val metadata = MetadataExtractor(modelFile)
            BufferedReader(InputStreamReader(
                metadata.getAssociatedFile(labelFilename), Charset.defaultCharset()
            )).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    Log.w(TAG, line!!)
                    d.labels.add(line!!)
                }
            }
            d.inputSize = inputSize
            try {
                val options = Interpreter.Options()
                options.setNumThreads(NUM_THREADS)
                options.setUseXNNPACK(true)
                d.tfLite = Interpreter(modelFile, options)
                d.tfLiteModel = modelFile
                d.tfLiteOptions = options
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            d.isModelQuantized = isQuantized
            // Pre-allocate buffers.
            val numBytesPerChannel = if (isQuantized) {
                1 // Quantized
            } else {
                4 // Floating point
            }
            d.imgData =
                ByteBuffer.allocateDirect(d.inputSize * d.inputSize * 3 * numBytesPerChannel)
            d.imgData!!.order(ByteOrder.nativeOrder())
            d.intValues = IntArray(d.inputSize * d.inputSize)
            d.outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
            d.outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
            d.outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
            d.numDetections = FloatArray(1)
            return d
        }
    }
}