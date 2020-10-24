package com.maxjokel.lens;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.fragment.app.Fragment;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/*
 *  FRAGMENT FOR 'MODEL SELECTOR' ELEMENT IN BOTTOM SHEET
 *
 * -> we use a fragment here, because we reuse this component in
 *    'ViewFinder' as well as in
 *    'CameraRoll'
 *
 * -> additionally, the actual selector UI is created dynamically based on the nets.json file
 *    in the /assets directory that this fragment obtains via the ListSingleton instance
 *
 *
 * -> should be the most elegant solution to this
 *
 *
 * */

public class ModelSelectorFragment extends Fragment {

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // instantiate new SharedPreferences object

    SharedPreferences prefs = null;
    SharedPreferences.Editor prefEditor = null;


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // related to 'ListSingleton'

    ListSingleton listSingletonInstance = ListSingleton.getInstance();
    List<ModelConfig> MODEL_LIST = listSingletonInstance.getList();

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // related to 'NewStaticClassifier'

    NewStaticClassifier newStaticClassifier = NewStaticClassifier.getInstance();


    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -


    // empty constructor
    public ModelSelectorFragment() {}

    public static ModelSelectorFragment newInstance() {
        return new ModelSelectorFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        // Please note the execution pipeline
        //   1. onCreate()
        //   2. onCreateView()   -> layout gets inflated
        //   3. onViewCreated()  -> we can now access the layout components
        //
        //
        // we dynamically create the RadioButtons in onViewCreated() for that reason


        // load sharedPreferences object and set up editor
        prefs = Objects.requireNonNull(this.getActivity()).getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE);

        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_model_selector, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        // this is called right after 'onCreateView()'


    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +          DYNAMICALLY SET UP RADIO BUTTONS             +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +


        // workaround for 'dp' instead of 'px' units
        float dpRatio = view.getContext().getResources().getDisplayMetrics().density;

        // init RadioGroup
        RadioGroup radioGroup = view.findViewById(R.id.modelSelector_RadioGroup);


        // iterate over list of models and create RadioButtons
        for (int i = 0; i < MODEL_LIST.size(); i++){

            ModelConfig m = MODEL_LIST.get(i);

            // create new RadioButton
            RadioButton radioButton = new RadioButton(view.getContext());

            // set ModelConfig attributes
            radioButton.setId(m.getId());
            radioButton.setText(m.getName());
            radioButton.setTag(m.getModelFilename());

            // set appearance
            radioButton.setTextColor(ContextCompat.getColor(view.getContext(), R.color.black));
            radioButton.setTextSize(14.0f);
            LinearLayout.LayoutParams paramsButton = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ); // [source: https://stackoverflow.com/a/12728484]
            paramsButton.setMargins(0,(int)(8 * dpRatio),0,0); // dp
            radioButton.setLayoutParams(paramsButton);
            CompoundButtonCompat.setButtonTintList(radioButton,
                    ColorStateList.valueOf(ContextCompat.getColor(view.getContext(), R.color.colorPrimary)));

            // create the 'info' TextView
            TextView textView = new TextView(view.getContext());

            textView.setText("Top 5 accuracy: " + m.getTop5accuracy());

            // set appearance
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins((int)(33 * dpRatio),(int)(-4 * dpRatio),0,0); // dp
            textView.setLayoutParams(params);
            textView.setTextColor(ContextCompat.getColor(view.getContext(), R.color.light_grey));
            textView.setTextSize(12.0f);

            // add both elements to the RadioGroup
            radioGroup.addView(radioButton);
            radioGroup.addView(textView);

        } // END of FOR loop


    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +           set INITIAL RadioGroup SELECTION            +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // get saved model Id from SharedPreferences
        int id = prefs.getInt("model", 0);
        RadioButton r = view.findViewById(id);

        if(id == 0 || r == null){
            r = view.findViewById(R.id.radioButton_FloatMobileNet);
        }

        // set initial selection
        r.setChecked(true);


    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    // +           NETWORK SELECTOR via RadioGroup             +
    // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init SharedPreferences Editor
        prefEditor = prefs.edit();

        // init RadioGroup event listener
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                // perform haptic feedback
                group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                int modelId;

                // check if it is the default RadioButton
                if(checkedId == R.id.radioButton_FloatMobileNet){
                    modelId = 0;
                } else {
                    modelId = view.findViewById(checkedId).getId();
                }

                // save selection to SharedPreferences
                prefEditor.putInt("model", modelId);
                prefEditor.apply();


                // trigger classifier update
                NewStaticClassifier.onConfigChanged();

            }
        });

    } // END of onViewCreated(...) - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

}