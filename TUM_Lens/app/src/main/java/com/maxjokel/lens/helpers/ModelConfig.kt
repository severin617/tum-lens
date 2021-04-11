package com.maxjokel.lens.helpers

import android.app.Activity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.io.IOException
import java.nio.charset.StandardCharsets

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

   We use objects of this class to represent the models specified in the 'assets/nets.json' file
   and store them in the ListSingleton instance.

   The ModelConfig objects are created directly from a passed JSON object.

   Please note that a models 'private int id;' is based on the filename .

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */
class ModelConfig {

    // name of model
    var name: String? = null
        private set

    // model id (based on the model's filename -> should be unique)
    var id = 0
        private set

    // model filename
    var modelFilename: String? = null
        private set

    // label filename
    var labelFilename: String? = null
        private set

    // label filename
    var top5accuracy: String? = null
        private set

    // image mean [float or quantized]
    private var imageMean = 0f

    // image standard deviation [float or quantized]
    private var imageStd = 0f

    // probability mean [float or quantized]
    private var probabilityMean = 0f

    // probability standard deviation [float or quantized]
    private var probabilityStd = 0f

    constructor(`object`: JSONObject?) {

        // IDEA
        //  - called from ListSingleton
        //  - create new 'ModelConfig' object from passed JSON object
        if (`object` == null) {
            LOGGER.e("Error in 'ModelConfig' constructor while reading JSON file. Object is null!")
            return
        }

        // set attributes:
        name = getJSONString(`object`, "name")
        modelFilename = getJSONString(`object`, "filename")
        id = modelFilename.hashCode()
        labelFilename = getJSONString(`object`, "labels")
        top5accuracy = getJSONString(`object`, "top5accuracy")
        imageMean = getJSONFloat(`object`, "image_mean")
        imageStd = getJSONFloat(`object`, "image_std")
        probabilityMean = getJSONFloat(`object`, "probability_mean")
        probabilityStd = getJSONFloat(`object`, "probability_std")
        LOGGER.i("Created ModelConfig for " + name + " with id " + id)
    }

    // create a new 'ModelConfig' object by 'filename'
    // 'Activity activity' argument is necessary for accessing '/assets' folder
    constructor(activity: Activity, filename: String) {

        // IDEA
        //  - parse 'nets.json'
        //  - find matching model in 'nets' array and set up object
        //  - if there is no match, set up with default 'Float MobileNet V1'

        // read in 'nets.json' file
        val jsonString = readJSON(activity)
        if (jsonString == null) {
            LOGGER.e("Error in 'ModelConfig' constructor while reading JSON file. String is null!")
            return
        }

        // now, process the files info
        var jsonObject: JSONObject? = null
        try {
            jsonObject = JSONObject(jsonString)
        } catch (e: JSONException) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing JSON file.")
            e.printStackTrace()
        }

        // load the 'nets' array
        var netsArray: JSONArray? = null
        try {
            netsArray = jsonObject!!.getJSONArray("nets")
        } catch (e: JSONException) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing the nets array.")
            e.printStackTrace()
        }
        if (netsArray == null) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing the nets array.")
            return
        }

        // iterate over array to find net with matching 'filename'
        for (i in 0 until netsArray.length()) {
            var obj = JSONObject()
            var obj_filename: String? = null
            try {
                obj = netsArray.getJSONObject(i)
                obj_filename = obj.getString("filename")
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            if (obj_filename != null && obj_filename == filename) {

                // set parameters:
                name = getJSONString(obj, "name")
                modelFilename = getJSONString(obj, "filename")
                labelFilename = getJSONString(obj, "labels")
                top5accuracy = getJSONString(obj, "top5accuracy")
                imageMean = getJSONFloat(obj, "image_mean")
                imageStd = getJSONFloat(obj, "image_std")
                probabilityMean = getJSONFloat(obj, "probability_mean")
                probabilityStd = getJSONFloat(obj, "probability_std")
                id = modelFilename.hashCode()
                LOGGER.i("Created ModelConfig for " + name)

                // leave loop and constructor
                return
            }
        }
        // if we get this far, there was no corresponding model in the 'nets' array;
        // so we init the object with the default model: 'Float MobileNet V1'
        ModelConfig()
    }

    // constructor for default 'Float MobileNet V1'
    // note that this constructor does not require any arguments
    constructor() {
        name = "Float MobileNet V1"
        modelFilename = "mobilenet_v1_224.tflite"
        labelFilename = "labels.txt"
        top5accuracy = "89,9%"
        imageMean = 127.5f
        imageStd = 127.5f
        probabilityMean = 0.0f
        probabilityStd = 1.0f
        id = modelFilename.hashCode()
        LOGGER.i("Created DEFAULT ModelConfig for " + name)
    }

    val preprocessNormalizeOp: TensorOperator
        get() = NormalizeOp(imageMean, imageStd)
    val postprocessNormalizeOp: TensorOperator
        get() = NormalizeOp(probabilityMean, probabilityStd)

    // ---------------------------------------------------------------------------------------------
    // HELPERS
    private fun getJSONString(o: JSONObject, p: String): String {
        var r = "null"
        try {
            r = o.getString(p)
        } catch (e: JSONException) {
            LOGGER.e("Error retrieving parameter $p from 'nets' JSON array.")
            e.printStackTrace()
        }
        return r
    }

    private fun getJSONFloat(o: JSONObject, p: String): Float {
        var r = 0.0
        try {
            r = o.getDouble(p)
        } catch (e: JSONException) {
            LOGGER.e("Error retrieving parameter $p from 'nets' JSON array.")
            e.printStackTrace()
        }
        return r.toFloat()
    }

    // read in 'nets.json' from '/assets' and return its contents as String
    // note: we need the activity context to access the '/assets' folder
    private fun readJSON(activity: Activity): String? {
        var jsonString: String? = null
        jsonString = try {
            val input = activity.assets.open("nets.json")
            val size = input.available()
            val buffer = ByteArray(size)
            input.read(buffer)
            input.close()
            String(buffer, StandardCharsets.UTF_8)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }
        return jsonString
    }

    companion object {
        // set up new LOGGER
        private val LOGGER = Logger()
    }
}