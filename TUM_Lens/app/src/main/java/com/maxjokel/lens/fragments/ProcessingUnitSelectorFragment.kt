package com.maxjokel.lens.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.maxjokel.lens.Classifier.Companion.onConfigChanged
import com.maxjokel.lens.R
import com.maxjokel.lens.helpers.ProcessingUnit
import java.util.*

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
*
* This fragment controls the processing unit selector in the BottomSheet of the ViewFinder activity;
* based on the enum above
*
* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */
class ProcessingUnitSelectorFragment  // Required empty public constructor
    : Fragment() {
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // layout elements
    private var chipGroup: ChipGroup? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // instantiate new SharedPreferences object
    var prefs: SharedPreferences? = null
    var prefEditor: SharedPreferences.Editor? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    private var PROCESSINGUNIT = ProcessingUnit.CPU // CPU is default

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // load sharedPreferences object and set up editor
        prefs = Objects.requireNonNull(this.activity)!!
            .getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_device_selector, container, false)

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;
        chipGroup = view.findViewById(R.id.chipGroup_processing_unit)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // this is called right after 'onCreateView()'

        // load processing unit from SharedPreferences
        val saved_device = prefs!!.getInt("processing_unit", 0)
        PROCESSINGUNIT = if (saved_device == ProcessingUnit.GPU.hashCode()) {
            ProcessingUnit.GPU
        } else if (saved_device == ProcessingUnit.NNAPI.hashCode()) {
            ProcessingUnit.NNAPI
        } else { // use CPU as default
            ProcessingUnit.CPU
        }

        // set initial selection
        val cName = "chip_" + PROCESSINGUNIT.name
        val cId = resources.getIdentifier(cName, "id", activity!!.packageName)
        val c: Chip = view.findViewById(cId)
        c.isChecked = true


        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +        PROCESSING UNIT SELECTOR via ChipGroup         +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init chip group and event listener
        chipGroup!!.setOnCheckedChangeListener { group, checkedId -> // perform haptic feedback
            group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            PROCESSINGUNIT = when (checkedId) {
                R.id.chip_GPU -> ProcessingUnit.GPU
                R.id.chip_NNAPI -> ProcessingUnit.NNAPI
                else -> ProcessingUnit.CPU
            }

            // init Editor and save selection to SharedPreferences
            prefEditor = prefs!!.edit()
            prefEditor?.run {
                putInt("processing_unit", PROCESSINGUNIT.hashCode())
                apply()
            }


            // trigger classifier update
            onConfigChanged()
        }
    }

    companion object {
        fun newInstance(): ProcessingUnitSelectorFragment {
            return ProcessingUnitSelectorFragment()
        }
    }
}