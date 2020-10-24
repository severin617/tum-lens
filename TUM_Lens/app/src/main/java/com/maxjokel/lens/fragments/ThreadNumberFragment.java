package com.maxjokel.lens.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.maxjokel.lens.Classifier;
import com.maxjokel.lens.R;

import java.util.Objects;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
 *
 * Fragment that controls the frame number selector in the BottomSheet of the ViewFinder activity
 *
 * + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class ThreadNumberFragment extends Fragment {


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // layout elements
    private TextView tv;

    private ImageButton btn_plus;
    private ImageButton btn_minus;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // instantiate new SharedPreferences object
    SharedPreferences prefs = null;
    SharedPreferences.Editor prefEditor = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // other global variables

    private int THREADNUMBER = 3; // default is 3


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // empty public constructor

    public ThreadNumberFragment() { }

    public static ThreadNumberFragment newInstance() {
        return new ThreadNumberFragment();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // load sharedPreferences object and set up editor
        prefs = Objects.requireNonNull(this.getActivity()).getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);


        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_thread_number, container, false);

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;
        tv = (TextView) view.findViewById(R.id.tv_threads);

        btn_minus = (ImageButton) view.findViewById(R.id.btn_threads_minus);
        btn_plus = (ImageButton) view.findViewById(R.id.btn_threads_plus);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        // this is called right after 'onCreateView()'

        // init SharedPreferences Editor
        prefEditor = prefs.edit();

        // load number of threads from SharedPreferences
        int saved_numberOfThreads = prefs.getInt("threads", 0);
        if ((saved_numberOfThreads > 0) && (saved_numberOfThreads <= 15)) {
            THREADNUMBER = saved_numberOfThreads; // update if within accepted range
        }

        updateThreadCounter();




        // decrease number of threads
        btn_minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(THREADNUMBER > 1){

                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                    THREADNUMBER--;

                    // update UI and save to SharedPreferences
                    updateThreadCounter();

                    // trigger classifier update
                    Classifier.onConfigChanged();

                }

            }
        });

        // increase number of threads
        btn_plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(THREADNUMBER < 15){

                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                    THREADNUMBER++;

                    // update UI and save to SharedPreferences
                    updateThreadCounter();

                    // trigger classifier update
                    Classifier.onConfigChanged();

                }
                
            }
        });


    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // updateThreadCounter()
    //
    //   - updates component in BottomSheet accordingly
    //   - saves number of threads to SharedPreferences object

    @SuppressLint("SetTextI18n")
    protected void updateThreadCounter(){

        // save number of threads to sharedPreferences
        prefEditor.putInt("threads", THREADNUMBER);
        prefEditor.apply();

        // update UI
        tv.setText("" + THREADNUMBER);

    }

}