package com.maxjokel.lens.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.maxjokel.lens.R;
import com.maxjokel.lens.helpers.Recognition;

import java.util.List;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
 *
 * Fragment for displaying the classification results (per frame)
 *
 * + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class PredictionsFragment extends Fragment {

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // layout elements

    private ConstraintLayout pf_row0, pf_row1, pf_row2, pf_row3, pf_row4;
    private TextView pf_description0, pf_description1, pf_description2, pf_description3, pf_description4;
    private TextView pf_confidence0, pf_confidence1, pf_confidence2, pf_confidence3, pf_confidence4;
    private TextView latency;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

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
        View view = inflater.inflate(R.layout.fragment_predictions, container, false);

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;

        pf_row0 = view.findViewById(R.id.pf_row0);
        pf_row1 = view.findViewById(R.id.pf_row1);
        pf_row2 = view.findViewById(R.id.pf_row2);
        pf_row3 = view.findViewById(R.id.pf_row3);
        pf_row4 = view.findViewById(R.id.pf_row4);

        pf_description0 = view.findViewById(R.id.pf_description0);
        pf_description1 = view.findViewById(R.id.pf_description1);
        pf_description2 = view.findViewById(R.id.pf_description2);
        pf_description3 = view.findViewById(R.id.pf_description3);
        pf_description4 = view.findViewById(R.id.pf_description4);

        pf_confidence0 = view.findViewById(R.id.pf_confidence0);
        pf_confidence1 = view.findViewById(R.id.pf_confidence1);
        pf_confidence2 = view.findViewById(R.id.pf_confidence2);
        pf_confidence3 = view.findViewById(R.id.pf_confidence3);
        pf_confidence4 = view.findViewById(R.id.pf_confidence4);

        latency = view.findViewById(R.id.time);

        return view;
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
    public void showRecognitionResults(List<Recognition> results, long time) {
    // this is called right after 'onCreateView()'

        if (results != null && results.size() > 0) {

            // get list length
            int resultsLength = results.size();

            // show all
            pf_row4.setVisibility(View.VISIBLE);
            pf_row3.setVisibility(View.VISIBLE);
            pf_row2.setVisibility(View.VISIBLE);
            pf_row1.setVisibility(View.VISIBLE);
            pf_row0.setVisibility(View.VISIBLE);

            // hide result rows, if there are not enough classes
            if(resultsLength < 5)
                pf_row4.setVisibility(View.GONE);
            if(resultsLength < 4)
                pf_row3.setVisibility(View.GONE);
            if(resultsLength < 3)
                pf_row2.setVisibility(View.GONE);
            if(resultsLength < 2)
                pf_row1.setVisibility(View.GONE);
            if(resultsLength < 1)
                pf_row0.setVisibility(View.GONE); // hide all


            // result at index 0
            Recognition recognition0 = results.get(0);
            if (recognition0 != null && recognition0.getTitle() != null && recognition0.getConfidence() != null) {
                pf_description0.setText(recognition0.getTitle());
                pf_confidence0.setText(String.format("%.1f", (100 * recognition0.getConfidence())) + "%");
            }

            // result at index 1
            Recognition recognition1 = results.get(1);
            if (recognition1 != null && recognition1.getTitle() != null && recognition1.getConfidence() != null) {
                pf_description1.setText(recognition1.getTitle());
                pf_confidence1.setText(String.format("%.1f", (100 * recognition1.getConfidence())) + "%");
            }

            // result at index 2
            Recognition recognition2 = results.get(2);
            if (recognition2 != null && recognition2.getTitle() != null && recognition2.getConfidence() != null) {
                pf_description2.setText(recognition2.getTitle());
                pf_confidence2.setText(String.format("%.1f", (100 * recognition2.getConfidence())) + "%");
            }

            // result at index 3
            Recognition recognition3 = results.get(3);
            if (recognition3 != null && recognition3.getTitle() != null && recognition3.getConfidence() != null) {
                pf_description3.setText(recognition3.getTitle());
                pf_confidence3.setText(String.format("%.1f", (100 * recognition3.getConfidence())) + "%");
            }

            // result at index 4
            Recognition recognition4 = results.get(4);
            if (recognition4 != null && recognition4.getTitle() != null && recognition4.getConfidence() != null) {
                pf_description4.setText(recognition4.getTitle());
                pf_confidence4.setText(String.format("%.1f", (100 * recognition4.getConfidence())) + "%");
            }

            // set time
            latency.setText("classifying this frame took " + time + " ms");

        } else { // hide all result rows

            pf_row4.setVisibility(View.GONE);
            pf_row3.setVisibility(View.GONE);
            pf_row2.setVisibility(View.GONE);
            pf_row1.setVisibility(View.GONE);
            pf_row0.setVisibility(View.GONE);

        }

    }

}