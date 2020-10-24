package com.maxjokel.lens.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.maxjokel.lens.R;
import com.maxjokel.lens.helpers.Recognition;
import com.maxjokel.lens.helpers.ResultItem;
import com.maxjokel.lens.helpers.ResultItemComparator;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
 *
 * Fragment for displaying the classification smoothed results
 *
 * + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */



public class SmoothedPredictionsFragment extends Fragment {


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // layout elements

    private LinearLayout placeholder, actual_result;
    private TextView desc0, desc1, desc2, desc3, desc4;
    private TextView conf0, conf1, conf2, conf3, conf4;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // init global variables related to 'showSmoothedRecognitionResults()'
    private int _counter = 0;
    Map<String, ResultItem> _map = new HashMap<String, ResultItem>();
    List<Recognition> _collection = new LinkedList<Recognition>();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public SmoothedPredictionsFragment() {
        // Required empty public constructor
    }

    public static SmoothedPredictionsFragment newInstance() {
        return new SmoothedPredictionsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_smoothed_predictions, container, false);

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;

        placeholder = view.findViewById(R.id.placeholder);
        actual_result = view.findViewById(R.id.actual_result);

        desc0 = view.findViewById(R.id.smooth_description0);
        desc1 = view.findViewById(R.id.smooth_description1);
        desc2 = view.findViewById(R.id.smooth_description2);
        desc3 = view.findViewById(R.id.smooth_description3);
        desc4 = view.findViewById(R.id.smooth_description4);

        conf0 = view.findViewById(R.id.smooth_confidence0);
        conf1 = view.findViewById(R.id.smooth_confidence1);
        conf2 = view.findViewById(R.id.smooth_confidence2);
        conf3 = view.findViewById(R.id.smooth_confidence3);
        conf4 = view.findViewById(R.id.smooth_confidence4);

        return view;
    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // showSmoothedRecognitionResults()
    //
    // Background:
    //   the classification is executed on a 'per frame' basis
    //   this makes it hard to read the actual classification results
    //
    // Idea:
    //   introduce 'artificial latency' into the process of displaying the classification results
    //   by taking an average over the last x classification results

    @UiThread
    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    public void showSmoothedRecognitionResults(List<Recognition> results) {

        if (_counter <= 9){

            // IDEA:
            // while the counter is less than 10, add the first three most promising classification results to a list

            if (results != null && results.size() > 0) { // if there are results, add them to the list

                Recognition recognition0 = results.get(0);
                if ((recognition0 != null) && (recognition0.getTitle() != null) && (recognition0.getConfidence() != null))  {
                    _collection.add(recognition0);
                }
                Recognition recognition1 = results.get(1);
                if ((recognition1 != null) && (recognition1.getTitle() != null) && (recognition1.getConfidence() != null))  {
                    _collection.add(recognition1);
                }
                Recognition recognition2 = results.get(2);
                if ((recognition2 != null) && (recognition2.getTitle() != null) && (recognition2.getConfidence() != null))  {
                    _collection.add(recognition2);
                }
                Recognition recognition3 = results.get(3);
                if ((recognition3 != null) && (recognition3.getTitle() != null) && (recognition3.getConfidence() != null))  {
                    _collection.add(recognition3);
                }
                Recognition recognition4 = results.get(4);
                if ((recognition4 != null) && (recognition4.getTitle() != null) && (recognition4.getConfidence() != null))  {
                    _collection.add(recognition4);
                }

                // increment counter
                _counter++;

            }

        } else { // now, the list should contain ~ 10x5 = 50 elements

            // IDEA
            //  - create a new 'List' data structure, that holds the custom 'ResultItem' objects
            //  - iterate over the last 10 classified frames and group multiple occurring results
            //     - if the new list is empty: just add the element
            //     - else try to find the previously added instance and update it
            //     - else add as a new element

            // new 'List' data structure
            List<ResultItem> list = new LinkedList<ResultItem>();

            // iterate
            for (int i=0; i < _collection.size(); i++) {

                Recognition a = _collection.get(i); // improve memory efficiency

                if (list.size() == 0){ // if list is empty, just add ResultItem to it

                    ResultItem c = new ResultItem(a.getTitle(), a.getConfidence());
                    list.add(c);

                } else { // else, look for previous occurrences

                    Boolean foundIt = false;

                    for (int j=0; j < list.size(); j++) {
                        if(list.get(j).getTitle() == a.getTitle()){ // is already element of list

                            // load element from List
                            ResultItem b = list.get(j);

                            // update element
                            b.incOccurrences();
                            b.addToConfidence(a.getConfidence());

                            // put back into List
                            list.set(j, b);

                            foundIt = true;
                        }
                    }

                    if(!foundIt){ // otherwise, just add it to the list
                        ResultItem c = new ResultItem(a.getTitle(), a.getConfidence());
                        list.add(c);
                    }

                }
            }

            // STEP 2: sort by number of occurrences and confidence level
            Collections.sort(list, new ResultItemComparator());


            // STEP 3: output the first 5 list elements
            if(list.size() >= 5){

                // hide placeholder and show actual results
                placeholder.setVisibility(View.GONE);
                actual_result.setVisibility(View.VISIBLE);


                ResultItem r0 = (ResultItem) list.get(0);
                desc0.setText(r0.getTitle() + " (" + r0.getOccurrences() + "x)");
                conf0.setText(String.format("%.1f", (100 * r0.getConfidence() / r0.getOccurrences())) + "%");

                ResultItem r1 = (ResultItem) list.get(1);
                desc1.setText(r1.getTitle() + " (" + r1.getOccurrences() + "x)");
                conf1.setText(String.format("%.1f", (100 * r1.getConfidence() / r1.getOccurrences())) + "%");

                ResultItem r2 = (ResultItem) list.get(2);
                desc2.setText(r2.getTitle() + " (" + r2.getOccurrences() + "x)");
                conf2.setText(String.format("%.1f", (100 * r2.getConfidence() / r2.getOccurrences())) + "%");

                ResultItem r3 = (ResultItem) list.get(3);
                desc3.setText(r3.getTitle() + " (" + r3.getOccurrences() + "x)");
                conf3.setText(String.format("%.1f", (100 * r3.getConfidence() / r3.getOccurrences())) + "%");

                ResultItem r4 = (ResultItem) list.get(4);
                desc4.setText(r4.getTitle() + " (" + r4.getOccurrences() + "x)");
                conf4.setText(String.format("%.1f", (100 * r4.getConfidence() / r4.getOccurrences())) + "%");

            }


            // reset data structures for next iteration
            _counter = 0;
            _map = new HashMap<String, ResultItem>();
            _collection = new LinkedList<Recognition>();

        }

    }

}