package com.maxjokel.lens;

import com.maxjokel.lens.helpers.ModelConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.maxjokel.lens.helpers.Logger;


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    SINGLETON pattern for creating a 'single source of truth'

    We init a new 'ListSingleton' instance when the app is launched.
    It parses the 'nets.json' file in the '/assets' directory and creates for each item in the
    'nets' array a 'ModelConfig' object by calling its constructor and passing the json array element.

    The List<ModelConfig> list is used in 'ModelSelectorFragment' to build its RadioGroup dynamically
    around the models specified in 'nets.json'

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */


public class ListSingleton {

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // set up new LOGGER

    private static final Logger LOGGER = new Logger();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // instance
    private static final ListSingleton instance = new ListSingleton();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // data
    private List<ModelConfig> list = newListFromJSON();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // private constructor, that can't be accessed from outside
    private ListSingleton(){ }

    public static ListSingleton getInstance() {
        return instance;
    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    private List<ModelConfig> newListFromJSON() {

        List<ModelConfig> l = new ArrayList<>();

        // IDEA
        //  - parse 'nets.json'
        //  - for each model specified in 'nets' array:
        //      - set up new 'ModelConfig' object
        //      - save to list

        // read in 'nets.json' file
        String jsonString = readJSON();
        if(jsonString == null){
            LOGGER.e("Error in 'ModelConfig' constructor while reading JSON file. String is null!");
            return l;
        }

        // now, process the files info
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(jsonString);
        } catch (JSONException e) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing JSON file.");
            e.printStackTrace();
            return l;
        }

        // load the 'nets' array
        JSONArray netsArray = null;
        try {
            netsArray = jsonObject.getJSONArray("nets");
        } catch (JSONException e) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing the nets array.");
            e.printStackTrace();
            return l;
        }

        // iterate over array to find net with matching 'filename'
        for (int i = 0; i < netsArray.length(); i++){

            try {

                // load JSON info
                JSONObject obj = new JSONObject();
                obj = netsArray.getJSONObject(i);

                // create new 'ModelConfig' object from JSON object
                ModelConfig m = new ModelConfig(obj);

                // add to List
                l.add(m);

            } catch (JSONException e) {
                LOGGER.e("Error while creating ModelConfig list");
                e.printStackTrace();
            }

        } // END of for loop

        return l;

    }






    // ---------------------------------------------------------------------------------------------
    // GETTERS

    public List<ModelConfig> getList(){
        return list;
    }


    // ---------------------------------------------------------------------------------------------
    // HELPERS


    // read in 'nets.json' from '/assets' and return its contents as String
    private String readJSON() {

        String jsonString = null;
        String file = "assets/nets.json";

        try {
            // circumvent Activity/ Context object by using .getClassLoader()
            InputStream input = this.getClass().getClassLoader().getResourceAsStream(file);

            if(input == null){
                LOGGER.e("# # # # # # # # # #");
                LOGGER.e("Error in readJSON(). could not find 'nets.json' file.");
                return null;
            }

            int size = input.available();
            byte[] buffer = new byte[size];
            input.read(buffer);
            input.close();

            jsonString = new String(buffer, StandardCharsets.UTF_8);

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }

        return jsonString;
    }




}