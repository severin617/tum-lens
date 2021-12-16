package com.maxjokel.lens.detection

import com.maxjokel.lens.helpers.Logger
import com.maxjokel.lens.helpers.ModelDetectConfig
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
   SINGLETON pattern for creating a 'single source of truth'

   We init a new 'ListSingleton' instance when the app is launched.
   It parses the 'netsDetection.json' file in the '/assets' directory and creates for each item in the
   'nets' array a 'ModelConfig' object by calling its constructor and passing the json array element.

   The List<ModelConfig> list is used in 'DetectionModelSelectorFragment' to build its RadioGroup dynamically
   around the models specified in 'netsDetection.json'
+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

object ListSingletonDetection {

    private val LOGGER = Logger()

    private const val CONFIG_FILEPATH = "assets/netsDetection.json"
    private const val RECOGNITION_ARRAY = "recognitionNets"

    val modelConfigs = modelConfigListFromJSON()

    private fun modelConfigListFromJSON(): List<ModelDetectConfig> {

        val list: MutableList<ModelDetectConfig> = ArrayList()

        val jsonString = readJSON(CONFIG_FILEPATH)

        val jsonObject = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing JSON file.")
            e.printStackTrace()
            return list
        }

        val netsArray = try {
            jsonObject.getJSONArray(RECOGNITION_ARRAY)
        } catch (e: JSONException) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing the $RECOGNITION_ARRAY array.")
            e.printStackTrace()
            return list
        }

        for (i in 0 until netsArray.length()) {
            try {
                val obj = netsArray.getJSONObject(i)
                list.add(ModelDetectConfig(obj))
            } catch (e: JSONException) {
                LOGGER.e(
                        "Error while parsing jsonObject at position $i in $netsArray. " +
                                "Object wasn't added to list of ModelConfigs."
                )
                e.printStackTrace()
            }
        }
        return list
    }

    private fun readJSON(filePath: String): String {
        val inputStream = javaClass.classLoader!!.getResourceAsStream(filePath)
        return inputStream.bufferedReader().use { it.readText() }
    }
}