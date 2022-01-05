package com.maxjokel.lens.fragments

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.fragment.app.Fragment
import com.maxjokel.lens.R
import com.maxjokel.lens.detection.CameraActivity
import com.maxjokel.lens.detection.DetectionActivity
import com.maxjokel.lens.detection.ListSingletonDetection
import com.maxjokel.lens.helpers.DownloadFiles
import java.io.File

class ModelSelectorDetectionFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var prefEditor: SharedPreferences.Editor

    private var MODEL_LIST = ListSingletonDetection.modelConfigs
    private lateinit var downloadFiles : DownloadFiles

    override fun onCreate(savedInstanceState: Bundle?) {

        DetectionActivity().initialize()
        // load sharedPreferences object and set up editor
        prefs = this.requireActivity()
                .getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)
        prefEditor = prefs.edit()
        downloadFiles = DownloadFiles(this.requireContext())
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_model_selector_detection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val root : String = Environment.getExternalStorageDirectory().absolutePath + "/models"
        val path = File(root)

        // workaround for 'dp' instead of 'px' units
        val dpRatio = view.context.resources.displayMetrics.density

        // init RadioGroup
        val radioGroup = view.findViewById<RadioGroup>(R.id.modelSelectorDet_RadioGroup)

        // iterate over list of models and create RadioButtons
        for (m in MODEL_LIST) {
            // create new RadioButton
            val radioButton = RadioButton(view.context)

            // set ModelConfig attributes
            radioButton.id = m.modelId
            radioButton.text = m.modelName
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
            CompoundButtonCompat.setButtonTintList(radioButton,
                    ColorStateList.valueOf(ContextCompat.getColor(view.context, R.color.colorPrimary))
            )

            // add download button
            val button = Button(view.context)
            button.tag = m.modelFilename
            button.text = "Download"
            button.textSize = 11.0f

            val paramsB = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            paramsB.gravity = Gravity.END
            button.layoutParams = paramsB
            button.setBackgroundResource(R.drawable.button_custom)

            val exactPath = File("$path/${button.tag}")
            if (exactPath.exists()) {
                button.isEnabled = false
            } else {
                button.setOnClickListener(View.OnClickListener {
                    Toast.makeText(context, "Clicked on button ${button.tag}", Toast.LENGTH_LONG).show()
                    button.isEnabled = false
                    downloadFiles.reqPermission(requireActivity(), button.tag.toString())
                })
            }


            radioGroup.addView(radioButton)
            radioGroup.addView(button)
        }

        // default button (first button)
        val defButton = view.findViewById<Button>(R.id.detect_tflite)
        val exactPath = File("$path/${defButton.tag}")

        if (exactPath.exists()) {   // the file is already downloaded
            defButton.isEnabled = false
        } else {  // the file is not downloaded
            defButton.setOnClickListener(View.OnClickListener {
                Toast.makeText(context, "Clicked on button ${defButton.tag}", Toast.LENGTH_LONG).show()
                defButton.isEnabled = false
                downloadFiles.reqPermission(requireActivity(), defButton.tag.toString())
            })
        }

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +           set INITIAL RadioGroup SELECTION            +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // get saved model Id from SharedPreferences
        val id = prefs.getInt("model_detection", 0)
        var r = view.findViewById<RadioButton>(id)
        if (id == 0 || r == null) {
            r = view.findViewById(R.id.radioButton_SSDMobileNet)
        }

        // set initial selection
        r!!.isChecked = true


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +           NETWORK SELECTOR via RadioGroup             +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        radioGroup.setOnCheckedChangeListener { group, checkedId -> // perform haptic feedback
            group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            var modelId = 0
            // save selection to SharedPreferences
            prefEditor.run {
                // check if it is the default RadioButton
                if (checkedId != R.id.radioButton_SSDMobileNet) {
                    modelId = view.findViewById<View>(checkedId).id
                }
                putInt("model_detection", modelId) // save selection to SharedPreferences
                apply()
            }
            DetectionActivity().reInitialize()
        }
    }

    companion object {
        fun newInstance(): ModelSelectorDetectionFragment {
            return ModelSelectorDetectionFragment()
        }
    }
}