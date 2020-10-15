package com.maxjokel.lens;

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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

enum ProcessingUnit {
    CPU,
    GPU,
    NNAPI
}


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
 *
 * This fragment controls the processing unit selector in the BottomSheet of the ViewFinder activity;
 * based on the enum above
 *
 * + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */




public class ProcessingUnitSelectorFragment extends Fragment {

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // instantiate new SharedPreferences object

    SharedPreferences prefs = null;
    SharedPreferences.Editor prefEditor = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // related to 'ClassifierEvents' Interface

    private List<ClassifierEvents> listeners = new ArrayList<ClassifierEvents>();

    public void addListener(ClassifierEvents toAdd) {
        listeners.add(toAdd);
    }


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private ProcessingUnit _processingUnit = ProcessingUnit.CPU; // CPU is default


    // Required empty public constructor
    public ProcessingUnitSelectorFragment() { }

    public static ProcessingUnitSelectorFragment newInstance() {
        return new ProcessingUnitSelectorFragment();
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
        return inflater.inflate(R.layout.fragment_device_selector, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        // this is called right after 'onCreateView()'

        // load processing unit from SharedPreferences
        int saved_device = prefs.getInt("processing_unit", 0);

        if(saved_device == ProcessingUnit.GPU.hashCode()){
            _processingUnit = ProcessingUnit.GPU;
        } else if(saved_device == ProcessingUnit.NNAPI.hashCode()){
            _processingUnit = ProcessingUnit.NNAPI;
        } else { // use CPU as default
            _processingUnit = ProcessingUnit.CPU;
        }

        // set initial selection
        String cName = "chip_" + _processingUnit.name();
        int cId = getResources().getIdentifier(cName, "id", getActivity().getPackageName());
        Chip c = getView().findViewById(cId);
        c.setChecked(true);


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +        PROCESSING UNIT SELECTOR via ChipGroup         +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init chip group and event listener
        ChipGroup chipGroup = getView().findViewById(R.id.chipGroup_processing_unit);
        chipGroup.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {

                // perform haptic feedback
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                switch (checkedId) {
                    case R.id.chip_GPU:
                        _processingUnit = ProcessingUnit.GPU;
                        break;
                    case R.id.chip_NNAPI:
                        _processingUnit = ProcessingUnit.NNAPI;
                        break;
                    default: // includes CPU
                        _processingUnit = ProcessingUnit.CPU;
                        break;
                }

                // init Editor and save selection to SharedPreferences
                prefEditor = prefs.edit();
                prefEditor.putInt("processing_unit", _processingUnit.hashCode());
                prefEditor.apply();

                // trigger classifier update   [source: https://stackoverflow.com/a/6270150]
                for (ClassifierEvents events : listeners) {
                    try {
                        events.onClassifierConfigChanged(getActivity());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        });

    }

}