package com.maxjokel.lens.helpers

import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.common.ops.NormalizeOp

/* We use objects of this class to represent the models specified in the 'assets/nets.json' file
   and store them in the ListSingleton instance.
   The ModelConfig objects are created directly from a passed JSON object. */
class ModelConfig {

    private enum class NeuralNetTypes { RECOGNITION, DETECTION }
    // TODO: add type to nets.json, add memberVar here and parse it accordingly in ListSingleton
    //       maybe this might also make sense to be in ListSingleton instead of here

    var modelName: String? = null
        private set
    var modelId = 0 // based on the model's filename -> should be unique
        private set
    var modelFilename: String? = null
        private set
    var labelFilename: String? = null
        private set
    var top5accuracy: String? = null
        private set

    private var imageMean = 0f
    private var imageStd = 0f
    private var probabilityMean = 0f
    private var probabilityStd = 0f

    constructor(jsonObj: JSONObject) { // called from ListSingleton
        modelName = getJSONString(jsonObj, "name")
        modelFilename = getJSONString(jsonObj, "filename")
        modelId = modelFilename.hashCode()
        labelFilename = getJSONString(jsonObj, "labels")
        top5accuracy = getJSONString(jsonObj, "top5accuracy")
        imageMean = getJSONFloat(jsonObj, "image_mean")
        imageStd = getJSONFloat(jsonObj, "image_std")
        probabilityMean = getJSONFloat(jsonObj, "probability_mean")
        probabilityStd = getJSONFloat(jsonObj, "probability_std")
        LOGGER.i("Created ModelConfig for $modelName with id $modelId")
    }

    constructor() { // constructor for default 'Float MobileNet V1'
        modelName = "Float MobileNet V1"
        modelFilename = "mobilenet_v1_224.tflite"
        labelFilename = "labels.txt"
        top5accuracy = "89,9%"
        imageMean = 127.5f
        imageStd = 127.5f
        probabilityMean = 0.0f
        probabilityStd = 1.0f
        modelId = modelFilename.hashCode()
        LOGGER.i("Created DEFAULT ModelConfig for $modelName")
    }

    val preprocessNormalizeOp: TensorOperator
        get() = NormalizeOp(imageMean, imageStd)
    val postprocessNormalizeOp: TensorOperator
        get() = NormalizeOp(probabilityMean, probabilityStd)

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

    companion object {
        private val LOGGER = Logger()
    }
}