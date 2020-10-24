package com.maxjokel.lens;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.UiThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class CameraRollPredictionsFragment extends Fragment {


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // layout elements

    private ConstraintLayout row0, row1, row2, row3, row4;
    private TextView description0, description1, description2, description3, description4;
    private TextView confidence0, confidence1, confidence2, confidence3, confidence4;
    private TextView latency;
    private LinearLayout placeholder, actual_result;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // Required empty public constructor
    public CameraRollPredictionsFragment() {}

    public static CameraRollPredictionsFragment newInstance() {
        return new CameraRollPredictionsFragment();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_camera_roll_predictions, container, false);

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;

        row0 = view.findViewById(R.id.row0);
        row1 = view.findViewById(R.id.row1);
        row2 = view.findViewById(R.id.row2);
        row3 = view.findViewById(R.id.row3);
        row4 = view.findViewById(R.id.row4);

        description0 = view.findViewById(R.id.description0);
        description1 = view.findViewById(R.id.description1);
        description2 = view.findViewById(R.id.description2);
        description3 = view.findViewById(R.id.description3);
        description4 = view.findViewById(R.id.description4);

        confidence0 = view.findViewById(R.id.confidence0);
        confidence1 = view.findViewById(R.id.confidence1);
        confidence2 = view.findViewById(R.id.confidence2);
        confidence3 = view.findViewById(R.id.confidence3);
        confidence4 = view.findViewById(R.id.confidence4);

        latency = view.findViewById(R.id.time);

        placeholder = view.findViewById(R.id.placeholder);
        actual_result = view.findViewById(R.id.actual_result);

        return view;
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

        if (results != null) {

            placeholder.setVisibility(View.GONE);
            actual_result.setVisibility(View.VISIBLE);

            // get list length
            int resultsLength = results.size();

            // hide result rows, if there are not enough classes
            if(resultsLength < 5)
                row4.setVisibility(View.GONE);
            if(resultsLength < 4)
                row3.setVisibility(View.GONE);
            if(resultsLength < 3)
                row2.setVisibility(View.GONE);
            if(resultsLength < 2)
                row1.setVisibility(View.GONE);
            if(resultsLength < 1)
                row0.setVisibility(View.GONE); // hide all


            // result at index 0
            Recognition recognition0 = results.get(0);
            if (recognition0 != null && recognition0.getTitle() != null && recognition0.getConfidence() != null) {
                description0.setText(recognition0.getTitle());
                confidence0.setText(String.format("%.1f", (100 * recognition0.getConfidence())) + "%");
            }

            // result at index 1
            Recognition recognition1 = results.get(1);
            if (recognition1 != null && recognition1.getTitle() != null && recognition1.getConfidence() != null) {
                description1.setText(recognition1.getTitle());
                confidence1.setText(String.format("%.1f", (100 * recognition1.getConfidence())) + "%");
            }

            // result at index 2
            Recognition recognition2 = results.get(2);
            if (recognition2 != null && recognition2.getTitle() != null && recognition2.getConfidence() != null) {
                description2.setText(recognition2.getTitle());
                confidence2.setText(String.format("%.1f", (100 * recognition2.getConfidence())) + "%");
            }

            // result at index 3
            Recognition recognition3 = results.get(3);
            if (recognition3 != null && recognition3.getTitle() != null && recognition3.getConfidence() != null) {
                description3.setText(recognition3.getTitle());
                confidence3.setText(String.format("%.1f", (100 * recognition3.getConfidence())) + "%");
            }

            // result at index 4
            Recognition recognition4 = results.get(4);
            if (recognition4 != null && recognition4.getTitle() != null && recognition4.getConfidence() != null) {
                description4.setText(recognition4.getTitle());
                confidence4.setText(String.format("%.1f", (100 * recognition4.getConfidence())) + "%");
            }

            // set time
            latency.setText("classifying this frame took " + time + " ms");

        } else { // hide all result rows

            row4.setVisibility(View.GONE);
            row3.setVisibility(View.GONE);
            row2.setVisibility(View.GONE);
            row1.setVisibility(View.GONE);
            row0.setVisibility(View.GONE);

        }

    }
}