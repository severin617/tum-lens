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

    private ProcessingUnit PROCESSINGUNIT = ProcessingUnit.CPU; // CPU is default


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
            PROCESSINGUNIT = ProcessingUnit.GPU;
        } else if(saved_device == ProcessingUnit.NNAPI.hashCode()){
            PROCESSINGUNIT = ProcessingUnit.NNAPI;
        } else { // use CPU as default
            PROCESSINGUNIT = ProcessingUnit.CPU;
        }

        // set initial selection
        String cName = "chip_" + PROCESSINGUNIT.name();
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
                        PROCESSINGUNIT = ProcessingUnit.GPU;
                        break;
                    case R.id.chip_NNAPI:
                        PROCESSINGUNIT = ProcessingUnit.NNAPI;
                        break;
                    default: // includes CPU
                        PROCESSINGUNIT = ProcessingUnit.CPU;
                        break;
                }

                // init Editor and save selection to SharedPreferences
                prefEditor = prefs.edit();
                prefEditor.putInt("processing_unit", PROCESSINGUNIT.hashCode());
                prefEditor.apply();


                // trigger classifier update
                NewStaticClassifier.onConfigChanged();

            }
        });

    }

}