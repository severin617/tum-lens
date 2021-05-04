package com.maxjokel.lens.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.fragment.app.Fragment
import com.maxjokel.lens.Classifier.Companion.getInstance
import com.maxjokel.lens.Classifier.Companion.onConfigChanged
import com.maxjokel.lens.ListSingleton.Companion.instance
import com.maxjokel.lens.R
import java.util.*

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
class ModelSelectorFragment  // NOTE: we need this line in order to initialize the classifier!
// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// empty constructor
    : Fragment() {
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // instantiate new SharedPreferences object
    var prefs: SharedPreferences? = null
    var prefEditor: SharedPreferences.Editor? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // related to 'ListSingleton'
    var listSingletonInstance = instance
    var MODEL_LIST = listSingletonInstance.list

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // related to 'Classifier'
    var newStaticClassifier = getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {

        // Please note the execution pipeline
        //   1. onCreate()
        //   2. onCreateView()   -> layout gets inflated
        //   3. onViewCreated()  -> we can now access the layout components
        //
        //
        // we dynamically create the RadioButtons in onViewCreated() for that reason


        // load sharedPreferences object and set up editor
        prefs = Objects.requireNonNull(this.activity)!!
            .getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_model_selector, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // this is called right after 'onCreateView()'


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +          DYNAMICALLY SET UP RADIO BUTTONS             +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +


        // workaround for 'dp' instead of 'px' units
        val dpRatio = view.context.resources.displayMetrics.density

        // init RadioGroup
        val radioGroup = view.findViewById<RadioGroup>(R.id.modelSelector_RadioGroup)


        // iterate over list of models and create RadioButtons
        for (i in MODEL_LIST.indices) {
            val m = MODEL_LIST[i]

            // create new RadioButton
            val radioButton = RadioButton(view.context)

            // set ModelConfig attributes
            radioButton.id = m.id
            radioButton.text = m.name
            radioButton.tag = m.modelFilename

            // set appearance
            radioButton.setTextColor(ContextCompat.getColor(view.context, R.color.black))
            radioButton.textSize = 14.0f
            val paramsButton = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ) // [source: https://stackoverflow.com/a/12728484]
            paramsButton.setMargins(0, (8 * dpRatio).toInt(), 0, 0) // dp
            radioButton.layoutParams = paramsButton
            CompoundButtonCompat.setButtonTintList(
                radioButton,
                ColorStateList.valueOf(ContextCompat.getColor(view.context, R.color.colorPrimary))
            )

            // create the 'info' TextView
            val textView = TextView(view.context)

//            textView.setText("Top 5 accuracy: " + m.getTop5accuracy());
            textView.text = "" + m.top5accuracy

            // set appearance
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins((33 * dpRatio).toInt(), (-4 * dpRatio).toInt(), 0, 0) // dp
            textView.layoutParams = params
            textView.setTextColor(ContextCompat.getColor(view.context, R.color.light_grey))
            textView.textSize = 12.0f

            // add both elements to the RadioGroup
            radioGroup.addView(radioButton)
            radioGroup.addView(textView)
        } // END of FOR loop


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +           set INITIAL RadioGroup SELECTION            +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // get saved model Id from SharedPreferences
        val id = prefs!!.getInt("model", 0)
        var r = view.findViewById<RadioButton>(id)
        if (id == 0 || r == null) {
            r = view.findViewById(R.id.radioButton_FloatMobileNet)
        }

        // set initial selection
        r!!.isChecked = true


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +           NETWORK SELECTOR via RadioGroup             +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init SharedPreferences Editor
        prefEditor = prefs!!.edit()

        // init RadioGroup event listener
        radioGroup.setOnCheckedChangeListener { group, checkedId -> // perform haptic feedback
            group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            var modelId: Int

            // check if it is the default RadioButton
            modelId = if (checkedId == R.id.radioButton_FloatMobileNet) {
                0
            } else {
                view.findViewById<View>(checkedId).id
            }

            // save selection to SharedPreferences
            prefEditor?.run {

                // check if it is the default RadioButton
                modelId = if (checkedId == R.id.radioButton_FloatMobileNet) {
                    0
                } else {
                    view.findViewById<View>(checkedId).id
                }

                // save selection to SharedPreferences
                putInt("model", modelId)
                apply()
            }


            // trigger classifier update
            onConfigChanged()
        }
    } // END of onViewCreated(...) - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    companion object {
        fun newInstance(): ModelSelectorFragment {
            return ModelSelectorFragment()
        }
    }
}