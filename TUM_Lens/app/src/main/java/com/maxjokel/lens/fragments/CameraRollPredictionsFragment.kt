package com.maxjokel.lens.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.maxjokel.lens.R
import com.maxjokel.lens.helpers.Recognition

class CameraRollPredictionsFragment  // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// Required empty public constructor
    : Fragment() {
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // layout elements
    private var row0: ConstraintLayout? = null
    private var row1: ConstraintLayout? = null
    private var row2: ConstraintLayout? = null
    private var row3: ConstraintLayout? = null
    private var row4: ConstraintLayout? = null
    private var description0: TextView? = null
    private var description1: TextView? = null
    private var description2: TextView? = null
    private var description3: TextView? = null
    private var description4: TextView? = null
    private var confidence0: TextView? = null
    private var confidence1: TextView? = null
    private var confidence2: TextView? = null
    private var confidence3: TextView? = null
    private var confidence4: TextView? = null
    private var latency: TextView? = null
    private var placeholder: LinearLayout? = null
    private var actual_result: LinearLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_camera_roll_predictions, container, false)

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;
        row0 = view.findViewById(R.id.row0)
        row1 = view.findViewById(R.id.row1)
        row2 = view.findViewById(R.id.row2)
        row3 = view.findViewById(R.id.row3)
        row4 = view.findViewById(R.id.row4)
        description0 = view.findViewById(R.id.description0)
        description1 = view.findViewById(R.id.description1)
        description2 = view.findViewById(R.id.description2)
        description3 = view.findViewById(R.id.description3)
        description4 = view.findViewById(R.id.description4)
        confidence0 = view.findViewById(R.id.confidence0)
        confidence1 = view.findViewById(R.id.confidence1)
        confidence2 = view.findViewById(R.id.confidence2)
        confidence3 = view.findViewById(R.id.confidence3)
        confidence4 = view.findViewById(R.id.confidence4)
        latency = view.findViewById(R.id.time)
        placeholder = view.findViewById(R.id.placeholder)
        actual_result = view.findViewById(R.id.actual_result)
        return view
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // showRecognitionResults()
    //
    // runs in UI Thread
    // displays the classification statistics to the user
    @UiThread
    @SuppressLint("DefaultLocale", "SetTextI18n")
    fun showRecognitionResults(results: List<Recognition?>?, time: Long) {

        // this is called right after 'onCreateView()'
        if (results != null) {
            placeholder!!.visibility = View.GONE
            actual_result!!.visibility = View.VISIBLE

            // get list length
            val resultsLength = results.size

            // hide result rows, if there are not enough classes
            if (resultsLength < 5) row4!!.visibility = View.GONE
            if (resultsLength < 4) row3!!.visibility = View.GONE
            if (resultsLength < 3) row2!!.visibility = View.GONE
            if (resultsLength < 2) row1!!.visibility = View.GONE
            if (resultsLength < 1) row0!!.visibility = View.GONE // hide all


            // result at index 0
            val recognition0 = results[0]
            if (recognition0 != null && recognition0.title != null && recognition0.confidence != null) {
                description0!!.text = recognition0.title
                confidence0!!.text = String.format("%.1f", 100 * recognition0.confidence) + "%"
            }

            // result at index 1
            val recognition1 = results[1]
            if (recognition1 != null && recognition1.title != null && recognition1.confidence != null) {
                description1!!.text = recognition1.title
                confidence1!!.text = String.format("%.1f", 100 * recognition1.confidence) + "%"
            }

            // result at index 2
            val recognition2 = results[2]
            if (recognition2 != null && recognition2.title != null && recognition2.confidence != null) {
                description2!!.text = recognition2.title
                confidence2!!.text = String.format("%.1f", 100 * recognition2.confidence) + "%"
            }

            // result at index 3
            val recognition3 = results[3]
            if (recognition3 != null && recognition3.title != null && recognition3.confidence != null) {
                description3!!.text = recognition3.title
                confidence3!!.text = String.format("%.1f", 100 * recognition3.confidence) + "%"
            }

            // result at index 4
            val recognition4 = results[4]
            if (recognition4 != null && recognition4.title != null && recognition4.confidence != null) {
                description4!!.text = recognition4.title
                confidence4!!.text = String.format("%.1f", 100 * recognition4.confidence) + "%"
            }

            // set time
            latency!!.text = "classifying this frame took $time ms"
        } else { // hide all result rows
            row4!!.visibility = View.GONE
            row3!!.visibility = View.GONE
            row2!!.visibility = View.GONE
            row1!!.visibility = View.GONE
            row0!!.visibility = View.GONE
        }
    }

    companion object {
        fun newInstance(): CameraRollPredictionsFragment {
            return CameraRollPredictionsFragment()
        }
    }
}