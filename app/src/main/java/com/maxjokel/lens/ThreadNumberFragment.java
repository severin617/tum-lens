package com.maxjokel.lens;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
 *
 * Fragment that controls the frame number selector in the BottomSheet of the ViewFinder activity
 *
 * + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */



public class ThreadNumberFragment extends Fragment {

    // instantiate new SharedPreferences object
    SharedPreferences prefs = null;
    SharedPreferences.Editor prefEditor = null;

    private int _numberOfThreads = 3; // default is 3


    // 'ClassifierEvents' Interface
    private List<ClassifierEvents> listeners = new ArrayList<ClassifierEvents>();




    public ThreadNumberFragment() {
        // Required empty public constructor
    }

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
        return inflater.inflate(R.layout.fragment_thread_number, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        // this is called right after 'onCreateView()'

        // init SharedPreferences Editor
        prefEditor = prefs.edit();

        // load number of threads from SharedPreferences
        int saved_numberOfThreads = prefs.getInt("threads", 0);
        if ((saved_numberOfThreads > 0) && (saved_numberOfThreads <= 15)) {
            _numberOfThreads = saved_numberOfThreads; // update if within accepted range
        }

        updateThreadCounter();




        // decrease number of threads
        getView().findViewById(R.id.btn_threads_minus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(_numberOfThreads > 1){

                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                    _numberOfThreads--;

                    // update UI and save to SharedPreferences
                    updateThreadCounter();

                    // trigger classifier update   [source: https://stackoverflow.com/a/6270150]
                    for (ClassifierEvents events : listeners)
                        events.onClassifierConfigChanged(getActivity());

                }

            }
        });

        // increase number of threads
        getView().findViewById(R.id.btn_threads_plus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(_numberOfThreads < 15){

                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                    _numberOfThreads++;

                    // update UI and save to SharedPreferences
                    updateThreadCounter();

                    // trigger classifier update   [source: https://stackoverflow.com/a/6270150]
                    for (ClassifierEvents events : listeners)
                        events.onClassifierConfigChanged(getActivity());

                }
                
            }
        });


    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // updateThreadCounter()
    //
    //   - updates component in BottomSheet accordingly
    //   - saves number of threads to SharedPreferences object

    protected void updateThreadCounter(){

        // save number of threads to sharedPreferences
        prefEditor.putInt("threads", _numberOfThreads);
        prefEditor.apply();

        // update UI
        TextView tv = getView().findViewById(R.id.tv_threads);
        tv.setText( "" + _numberOfThreads);
    }


    public void addListener(ClassifierEvents toAdd) {
        listeners.add(toAdd);
    }


}