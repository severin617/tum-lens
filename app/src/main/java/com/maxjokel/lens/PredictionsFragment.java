package com.maxjokel.lens;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import tflite.Classifier;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PredictionsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PredictionsFragment extends Fragment {

    public PredictionsFragment() {
        // Required empty public constructor
    }

    public static PredictionsFragment newInstance() {
        return new PredictionsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_predictions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // this is called right after 'onCreateView()'
    }



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // showRecognitionResults()
    //
    // runs in UI Thread
    // displays the classification statistics to the user

    @UiThread
    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    public void showRecognitionResults(List<StandaloneClassifier.Recognition> results, long time) {

        // this is called right after 'onCreateView()'

        if (results != null) {

            // get list length
            int resultsLength = results.size();

            // hide result rows, if there are not enough classes
            if(resultsLength < 5)
                getView().findViewById(R.id.pf_row4).setVisibility(View.GONE);
            if(resultsLength < 4)
                getView().findViewById(R.id.pf_row3).setVisibility(View.GONE);
            if(resultsLength < 3)
                getView().findViewById(R.id.pf_row2).setVisibility(View.GONE);
            if(resultsLength < 2)
                getView().findViewById(R.id.pf_row1).setVisibility(View.GONE);
            if(resultsLength < 1)
                getView().findViewById(R.id.pf_row0).setVisibility(View.GONE); // hide all


            // result at index 0
            StandaloneClassifier.Recognition recognition0 = results.get(0);
            if (recognition0 != null && recognition0.getTitle() != null && recognition0.getConfidence() != null) {
                final TextView desc = getView().findViewById(R.id.pf_description0);
                final TextView conf = getView().findViewById(R.id.pf_confidence0);
                desc.setText(recognition0.getTitle());
                conf.setText(String.format("%.1f", (100 * recognition0.getConfidence())) + "%");
            }

            // result at index 1
            StandaloneClassifier.Recognition recognition1 = results.get(1);
            if (recognition1 != null && recognition1.getTitle() != null && recognition1.getConfidence() != null) {
                final TextView desc = getView().findViewById(R.id.pf_description1);
                final TextView conf = getView().findViewById(R.id.pf_confidence1);
                desc.setText(recognition1.getTitle());
                conf.setText(String.format("%.1f", (100 * recognition1.getConfidence())) + "%");
            }

            // result at index 2
            StandaloneClassifier.Recognition recognition2 = results.get(2);
            if (recognition2 != null && recognition2.getTitle() != null && recognition2.getConfidence() != null) {
                final TextView desc = getView().findViewById(R.id.pf_description2);
                final TextView conf = getView().findViewById(R.id.pf_confidence2);
                desc.setText(recognition2.getTitle());
                conf.setText(String.format("%.1f", (100 * recognition2.getConfidence())) + "%");
            }

            // result at index 3
            StandaloneClassifier.Recognition recognition3 = results.get(3);
            if (recognition3 != null && recognition3.getTitle() != null && recognition3.getConfidence() != null) {
                final TextView desc = getView().findViewById(R.id.pf_description3);
                final TextView conf = getView().findViewById(R.id.pf_confidence3);
                desc.setText(recognition3.getTitle());
                conf.setText(String.format("%.1f", (100 * recognition3.getConfidence())) + "%");
            }

            // result at index 4
            StandaloneClassifier.Recognition recognition4 = results.get(4);
            if (recognition4 != null && recognition4.getTitle() != null && recognition4.getConfidence() != null) {
                final TextView desc = getView().findViewById(R.id.pf_description4);
                final TextView conf = getView().findViewById(R.id.pf_confidence4);
                desc.setText(recognition4.getTitle());
                conf.setText(String.format("%.1f", (100 * recognition4.getConfidence())) + "%");
            }

            // set time
            final TextView textView = getView().findViewById(R.id.time);
            textView.setText("classifying this frame took " + time + " ms");

        } else { // hide all result rows

            getView().findViewById(R.id.pf_row4).setVisibility(View.GONE);
            getView().findViewById(R.id.pf_row3).setVisibility(View.GONE);
            getView().findViewById(R.id.pf_row2).setVisibility(View.GONE);
            getView().findViewById(R.id.pf_row1).setVisibility(View.GONE);
            getView().findViewById(R.id.pf_row0).setVisibility(View.GONE);

        }

    }

}