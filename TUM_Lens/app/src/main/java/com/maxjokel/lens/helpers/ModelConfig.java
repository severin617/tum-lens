package com.maxjokel.lens.helpers;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    We use objects of this class to represent the models specified in the 'assets/nets.json' file
    and store them in the ListSingleton instance.

    The ModelConfig objects are created directly from a passed JSON object.

    Please note that a models 'private int id;' is based on the filename .

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class ModelConfig {

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // set up new LOGGER

    private static final Logger LOGGER = new Logger();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // set up global variables

    // name of model
    private String name;

    // model id (based on the model's filename -> should be unique)
    private int id;

    // model filename
    private String filenameModel;

    // label filename
    private String filenameLabels;

    // label filename
    private String top5accuracy;

    // image mean [float or quantized]
    private float imageMean;

    // image standard deviation [float or quantized]
    private float imageStd;

    // probability mean [float or quantized]
    private float probabilityMean;

    // probability standard deviation [float or quantized]
    private float probabilityStd;



    public ModelConfig(JSONObject object){

        // IDEA
        //  - called from ListSingleton
        //  - create new 'ModelConfig' object from passed JSON object

        if(object == null){
            LOGGER.e("Error in 'ModelConfig' constructor while reading JSON file. Object is null!");
            return;
        }

        // set attributes:
        this.name = getJSONString(object, "name");

        this.filenameModel = getJSONString(object, "filename");
        this.id = this.filenameModel.hashCode();

        this.filenameLabels = getJSONString(object, "labels");

        this.top5accuracy = getJSONString(object, "top5accuracy");

        this.imageMean = getJSONFloat(object, "image_mean");
        this.imageStd = getJSONFloat(object, "image_std");

        this.probabilityMean = getJSONFloat(object, "probability_mean");
        this.probabilityStd = getJSONFloat(object, "probability_std");

        LOGGER.i("Created ModelConfig for " + this.getName() + " with id " + this.getId());

    }





    // create a new 'ModelConfig' object by 'filename'
    // 'Activity activity' argument is necessary for accessing '/assets' folder
    public ModelConfig(Activity activity, String filename){

        // IDEA
        //  - parse 'nets.json'
        //  - find matching model in 'nets' array and set up object
        //  - if there is no match, set up with default 'Float MobileNet V1'


        // read in 'nets.json' file
        String jsonString = readJSON(activity);
        if(jsonString == null){
            LOGGER.e("Error in 'ModelConfig' constructor while reading JSON file. String is null!");
            return;
        }

        // now, process the files info
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(jsonString);
        } catch (JSONException e) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing JSON file.");
            e.printStackTrace();
        }

        // load the 'nets' array
        JSONArray netsArray = null;
        try {
            netsArray = jsonObject.getJSONArray("nets");
        } catch (JSONException e) {
            LOGGER.e("Error in 'ModelConfig' constructor while parsing the nets array.");
            e.printStackTrace();
        }

        if(netsArray == null){
            LOGGER.e("Error in 'ModelConfig' constructor while parsing the nets array.");
            return;
        }

        // iterate over array to find net with matching 'filename'
        for (int i = 0; i < netsArray.length(); i++){

            JSONObject obj = new JSONObject();
            String obj_filename = null;

            try {
                obj = netsArray.getJSONObject(i);
                obj_filename = obj.getString("filename");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if(obj_filename != null && obj_filename.equals(filename)){

                // set parameters:
                this.name = getJSONString(obj, "name");
                this.filenameModel = getJSONString(obj, "filename");
                this.filenameLabels = getJSONString(obj, "labels");
                this.top5accuracy = getJSONString(obj, "top5accuracy");

                this.imageMean = getJSONFloat(obj, "image_mean");
                this.imageStd = getJSONFloat(obj, "image_std");
                this.probabilityMean = getJSONFloat(obj, "probability_mean");
                this.probabilityStd = getJSONFloat(obj, "probability_std");

                this.id = this.filenameModel.hashCode();


                LOGGER.i("Created ModelConfig for " + this.name);

                // leave loop and constructor
                return;
            }

        } // END of for loop



        // if we get this far, there was no corresponding model in the 'nets' array;
        // so we init the object with the default model: 'Float MobileNet V1'
        new ModelConfig();

    }


    // constructor for default 'Float MobileNet V1'
    // note that this constructor does not require any arguments
    public ModelConfig(){

        this.name = "Float MobileNet V1";
        this.filenameModel = "mobilenet_v1_224.tflite";
        this.filenameLabels = "labels.txt";
        this.top5accuracy = "89,9%";
        this.imageMean = 127.5f;
        this.imageStd = 127.5f;
        this.probabilityMean = 0.0f;
        this.probabilityStd = 1.0f;
        this.id = this.filenameModel.hashCode();

        LOGGER.i("Created DEFAULT ModelConfig for " + this.name);

    }






    // ---------------------------------------------------------------------------------------------
    // GETTERS

    public String getName() { return this.name; }

    public int getId() { return this.id; }

    public String getModelFilename() { return this.filenameModel; }

    public String getLabelFilename() { return this.filenameLabels; }

    public String getTop5accuracy() { return this.top5accuracy; }

    public TensorOperator getPreprocessNormalizeOp() {
        return new NormalizeOp(this.imageMean, this.imageStd);
    }

    public TensorOperator getPostprocessNormalizeOp() {
        return new NormalizeOp(this.probabilityMean, this.probabilityStd);
    }

    // ---------------------------------------------------------------------------------------------
    // HELPERS


    private String getJSONString(JSONObject o, String p){

        String r = "null";

        try {
            r = o.getString(p);
        } catch (JSONException e) {
            LOGGER.e("Error retrieving parameter " + p + " from 'nets' JSON array.");
            e.printStackTrace();
        }

        return r;
    }

    private Float getJSONFloat(JSONObject o, String p){

        double r = 0.0;

        try {
            r = o.getDouble(p);
        } catch (JSONException e) {
            LOGGER.e("Error retrieving parameter " + p + " from 'nets' JSON array.");
            e.printStackTrace();
        }

        return (float)r;
    }




    // read in 'nets.json' from '/assets' and return its contents as String
    // note: we need the activity context to access the '/assets' folder
    private String readJSON(Activity activity) {

        String jsonString = null;

        try {
            InputStream input = activity.getAssets().open("nets.json");

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