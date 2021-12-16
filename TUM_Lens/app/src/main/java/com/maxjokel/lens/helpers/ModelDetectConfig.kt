package com.maxjokel.lens.helpers

import org.json.JSONException
import org.json.JSONObject

/* We use objects of this class to represent the models specified in the 'assets/netsDetection.json' file
   and store them in the ListSingleton instance.
   The ModelConfig objects are created directly from a passed JSON object. */

class ModelDetectConfig {

    var modelName: String? = null
        private set
    var modelId = 0 // based on the model's filename -> should be unique
        private set
    var modelFilename: String? = null
        private set
    var labelFilename: String? = null
        private set

    var imageMean = 0f
    var imageStd = 0f

    var inputSize = 0
    var quantized = false

    constructor(jsonObj: JSONObject) { // called from ListSingleton
        modelName = getJSONString(jsonObj, "name")
        modelFilename = getJSONString(jsonObj, "filename")
        modelId = modelFilename.hashCode()
        labelFilename = getJSONString(jsonObj, "labels")
        imageMean = getJSONFloat(jsonObj, "image_mean")
        imageStd = getJSONFloat(jsonObj, "image_std")
        inputSize = getJSONInt(jsonObj, "input_size")
        quantized = getJSONBool(jsonObj, "quantized")

        ModelDetectConfig.LOGGER.i("Created ModelConfig for $modelName with id $modelId")
    }

    constructor() { // constructor for default 'Float MobileNet V1'
        modelName = "SSD MobileNetV1"
        modelFilename = "detect.tflite"
        labelFilename = "labelmap.txt"
        imageMean = 127.5f
        imageStd = 127.5f
        modelId = 0
        inputSize =  300
        quantized = true
//        ModelConfig.LOGGER.i("Created DEFAULT ModelConfig for $modelName")
    }

    private fun getJSONString(o: JSONObject, p: String): String {
        return try {
            o.getString(p)
        } catch (e: JSONException) {
            LOGGER.e("Error retrieving parameter $p from JSON array.")
            e.printStackTrace()
            "null"
        }
    }

    private fun getJSONFloat(o: JSONObject, p: String): Float {
        return try {
            o.getDouble(p).toFloat()
        } catch (e: JSONException) {
            LOGGER.e("Error retrieving parameter $p from JSON array.")
            e.printStackTrace()
            0.0f
        }
    }

    private fun getJSONInt(o: JSONObject, p: String): Int {
        return try {
            o.getInt(p)
        } catch (e: JSONException) {
            LOGGER.e("Error retrieving parameter $p from JSON array.")
            e.printStackTrace()
            0
        }
    }

    private fun getJSONBool(o: JSONObject, p: String): Boolean {
        return try {
            o.getBoolean(p)
        } catch (e: JSONException) {
            LOGGER.e("Error retrieving parameter $p from JSON array.")
            e.printStackTrace()
            false
        }
    }

    companion object {
        private val LOGGER = Logger()
    }

}