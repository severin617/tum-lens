package com.maxjokel.lens;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import helpers.Logger;

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

    // sizes of model's input layer
    private int[] inputSizeArray = new int[4];



    // TODO: 25.09.2020, 15:30 Uhr
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

        this.inputSizeArray = getJSONInputSizeArray(object);

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

                this.inputSizeArray = getJSONInputSizeArray(obj);

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
        this.inputSizeArray = new int[] {1, 224, 224, 3};
        this.id = this.filenameModel.hashCode();

        LOGGER.i("Created DEFAULT ModelConfig for " + this.name);

    }






    // ---------------------------------------------------------------------------------------------
    // GETTERS

    protected String getName() { return this.name; }

    protected int getId() { return this.id; }

    protected String getModelFilename() { return this.filenameModel; }

    protected String getLabelFilename() { return this.filenameLabels; }

    protected String getTop5accuracy() { return this.top5accuracy; }

    protected TensorOperator getPreprocessNormalizeOp() {
        return new NormalizeOp(this.imageMean, this.imageStd);
    }

    protected TensorOperator getPostprocessNormalizeOp() {
        return new NormalizeOp(this.probabilityMean, this.probabilityStd);
    }

    protected int[] getInputSizeArray() {
        return this.inputSizeArray;
    }


    // ---------------------------------------------------------------------------------------------
    // HELPERS


    private String getJSONString(JSONObject o, String p){

        String r = null;

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

    private int[] getJSONInputSizeArray(JSONObject o){

        JSONArray t = null;
        int[] r = new int[4];

        try {
            t = o.getJSONArray("inputSizeArray");

            r[0] = t.getInt(0);
            r[1] = t.getInt(1);
            r[2] = t.getInt(2);
            r[3] = t.getInt(3);

        } catch (JSONException e) {
            LOGGER.e("Error retrieving parameter " + "inputSizeArray" + " from 'nets' JSON array.");
            e.printStackTrace();
        }

        return r;
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
