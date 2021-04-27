package com.maxjokel.lens

import com.maxjokel.lens.helpers.Logger
import com.maxjokel.lens.helpers.ModelConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

   SINGLETON pattern for creating a 'single source of truth'

   We init a new 'ListSingleton' instance when the app is launched.
   It parses the 'nets.json' file in the '/assets' directory and creates for each item in the
   'nets' array a 'ModelConfig' object by calling its constructor and passing the json array element.

   The List<ModelConfig> list is used in 'ModelSelectorFragment' to build its RadioGroup dynamically
   around the models specified in 'nets.json'

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

class ListSingleton

// private constructor, that can't be accessed from outside
private constructor() {

    // data
    val list = newListFromJSON()


    private fun newListFromJSON(): List<ModelConfig> {
        // IDEA
        //  - parse 'nets.json'
        //  - for each model specified in 'nets' array:
        //      - set up new 'ModelConfig' object
        //      - save to list

        val l: MutableList<ModelConfig> = ArrayList()

        // read in 'nets.json' file
        val jsonString = readJSON()
        if (jsonString == null) {
            LOGGER.e("Error in 'ModelConfig' constructor while reading JSON file. String is null!")
            return l
        }

        // now, process the files info
        var jsonObject = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing JSON file.")
            e.printStackTrace()
            return l
        }

        // load the 'nets' array
        var netsArray = try {
            jsonObject.getJSONArray("nets")
        } catch (e: JSONException) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing the nets array.")
            e.printStackTrace()
            return l
        }

        // iterate over array to find net with matching 'filename'
        for (i in 0 until netsArray!!.length()) {
            try {

                // load JSON info
                var obj = netsArray.getJSONObject(i)

                // create new 'ModelConfig' object from JSON object
                val m = ModelConfig(obj)

                // add to List
                l.add(m)
            } catch (e: JSONException) {
                LOGGER.e("Error while creating ModelConfig list")
                e.printStackTrace()
            }
        } // END of for loop
        return l
    }


    // read in 'nets.json' from '/assets' and return its contents as String
    private fun readJSON(): String? {
        val file = "assets/nets.json"
        var jsonString = try {
            // circumvent Activity/ Context object by using .getClassLoader()
            val input = this.javaClass.classLoader!!.getResourceAsStream(file)
            if (input == null) {
                LOGGER.e("# # # # # # # # # #")
                LOGGER.e("Error in readJSON(). could not find 'nets.json' file.")
                return null
            }
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

        // instance
        @JvmStatic
        val instance = ListSingleton()
    }
}