package com.maxjokel.lens.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.maxjokel.lens.R
import com.maxjokel.lens.helpers.Recognition

// Fragment for displaying the classification results (per frame)
class PredictionsFragment: Fragment() {
    // layout elements
    private var pf_row0: ConstraintLayout? = null
    private var pf_row1: ConstraintLayout? = null
    private var pf_row2: ConstraintLayout? = null
    private var pf_row3: ConstraintLayout? = null
    private var pf_row4: ConstraintLayout? = null
    private var pf_description0: TextView? = null
    private var pf_description1: TextView? = null
    private var pf_description2: TextView? = null
    private var pf_description3: TextView? = null
    private var pf_description4: TextView? = null
    private var pf_confidence0: TextView? = null
    private var pf_confidence1: TextView? = null
    private var pf_confidence2: TextView? = null
    private var pf_confidence3: TextView? = null
    private var pf_confidence4: TextView? = null
    private var latency: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_predictions, container, false)

        pf_row0 = view.findViewById(R.id.pf_row0)
        pf_row1 = view.findViewById(R.id.pf_row1)
        pf_row2 = view.findViewById(R.id.pf_row2)
        pf_row3 = view.findViewById(R.id.pf_row3)
        pf_row4 = view.findViewById(R.id.pf_row4)
        pf_description0 = view.findViewById(R.id.pf_description0)
        pf_description1 = view.findViewById(R.id.pf_description1)
        pf_description2 = view.findViewById(R.id.pf_description2)
        pf_description3 = view.findViewById(R.id.pf_description3)
        pf_description4 = view.findViewById(R.id.pf_description4)
        pf_confidence0 = view.findViewById(R.id.pf_confidence0)
        pf_confidence1 = view.findViewById(R.id.pf_confidence1)
        pf_confidence2 = view.findViewById(R.id.pf_confidence2)
        pf_confidence3 = view.findViewById(R.id.pf_confidence3)
        pf_confidence4 = view.findViewById(R.id.pf_confidence4)
        latency = view.findViewById(R.id.time)
        return view
    }

    // runs in UI Thread and displays the classification statistics to the user
    @UiThread
    @SuppressLint("DefaultLocale", "SetTextI18n")
    fun showRecognitionResults(results: List<Recognition?>?, time: Long) {
        // this is called right after 'onCreateView()'
        if (results != null && results.size > 0) {

            // get list length
            val resultsLength = results.size

            // show all
            pf_row4!!.visibility = View.VISIBLE
            pf_row3!!.visibility = View.VISIBLE
            pf_row2!!.visibility = View.VISIBLE
            pf_row1!!.visibility = View.VISIBLE
            pf_row0!!.visibility = View.VISIBLE

            // hide result rows, if there are not enough classes
            if (resultsLength < 5) pf_row4!!.visibility = View.GONE
            if (resultsLength < 4) pf_row3!!.visibility = View.GONE
            if (resultsLength < 3) pf_row2!!.visibility = View.GONE
            if (resultsLength < 2) pf_row1!!.visibility = View.GONE
            if (resultsLength < 1) pf_row0!!.visibility = View.GONE // hide all


            // result at index 0
            val recognition0 = results[0]
            if (recognition0 != null && recognition0.title != null && recognition0.confidence != null) {
                pf_description0!!.text = recognition0.title
                pf_confidence0!!.text = String.format("%.1f", 100 * recognition0.confidence) + "%"
            }

            // result at index 1
            val recognition1 = results[1]
            if (recognition1 != null && recognition1.title != null && recognition1.confidence != null) {
                pf_description1!!.text = recognition1.title
                pf_confidence1!!.text = String.format("%.1f", 100 * recognition1.confidence) + "%"
            }

            // result at index 2
            val recognition2 = results[2]
            if (recognition2 != null && recognition2.title != null && recognition2.confidence != null) {
                pf_description2!!.text = recognition2.title
                pf_confidence2!!.text = String.format("%.1f", 100 * recognition2.confidence) + "%"
            }

            // result at index 3
            val recognition3 = results[3]
            if (recognition3 != null && recognition3.title != null && recognition3.confidence != null) {
                pf_description3!!.text = recognition3.title
                pf_confidence3!!.text = String.format("%.1f", 100 * recognition3.confidence) + "%"
            }

            // result at index 4
            val recognition4 = results[4]
            if (recognition4 != null && recognition4.title != null && recognition4.confidence != null) {
                pf_description4!!.text = recognition4.title
                pf_confidence4!!.text = String.format("%.1f", 100 * recognition4.confidence) + "%"
            }

            // set time
            latency!!.text = "classifying this frame took $time ms"
        } else { // hide all result rows
            pf_row4!!.visibility = View.GONE
            pf_row3!!.visibility = View.GONE
            pf_row2!!.visibility = View.GONE
            pf_row1!!.visibility = View.GONE
            pf_row0!!.visibility = View.GONE
        }
    }

    companion object {
        fun newInstance(): PredictionsFragment {
            return PredictionsFragment()
        }
    }
}