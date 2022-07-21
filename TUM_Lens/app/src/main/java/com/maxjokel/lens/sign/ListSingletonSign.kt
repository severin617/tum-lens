package com.maxjokel.lens.sign

import com.maxjokel.lens.classification.ListSingleton
import com.maxjokel.lens.helpers.App.Companion.context
import com.maxjokel.lens.helpers.Logger
import com.maxjokel.lens.helpers.ModelConfig
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

object ListSingletonSign {
    private val LOGGER = Logger()

    private const val CONFIG_FILEPATH = "assets/netsSign.json"
    private const val RECOGNITION_ARRAY = "recognitionNets"

    val modelConfigs = modelConfigListFromJSON()

    private fun modelConfigListFromJSON(): List<ModelConfig> {

        val list: MutableList<ModelConfig> = ArrayList()

        val jsonString = ListSingletonSign.readJSON(ListSingletonSign.CONFIG_FILEPATH)

        val jsonObject = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            ListSingletonSign.LOGGER.e("Error in 'ModelConfig' constructor while parsing JSON file.")
            e.printStackTrace()
            return list
        }

        val netsArray = try {
            jsonObject.getJSONArray(ListSingletonSign.RECOGNITION_ARRAY)
        } catch (e: JSONException) {
            ListSingletonSign.LOGGER.e("Error in 'ModelConfig' constructor while parsing the ${ListSingletonSign.RECOGNITION_ARRAY} array.")
            e.printStackTrace()
            return list
        }

        for (i in 0 until netsArray.length()) {
            try {
                val obj = netsArray.getJSONObject(i)
                list.add(ModelConfig(obj))
            } catch (e: JSONException) {
                ListSingletonSign.LOGGER.e(
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