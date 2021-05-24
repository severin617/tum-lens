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

class CameraRollPredictionsFragment : Fragment() {

    private val maxResults = 5
    private val rows: MutableList<ConstraintLayout?> = mutableListOf()
    private val descriptions: MutableList<TextView?> = mutableListOf()
    private val confidences: MutableList<TextView?> = mutableListOf()
    private var latency: TextView? = null
    private var placeholder: LinearLayout? = null
    private var actualResult: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_camera_roll_predictions, container, false)

        val packageName = view.context.packageName
        for (id in 0 until maxResults) {
            val rowId = resources.getIdentifier("row"+id, "id", packageName)
            val descId = resources.getIdentifier("description"+id, "id", packageName)
            val confId = resources.getIdentifier("confidence"+id, "id", packageName)
            rows.add(view.findViewById(rowId))
            descriptions.add(view.findViewById(descId))
            confidences.add(view.findViewById(confId))
        }
        latency = view.findViewById(R.id.time)
        placeholder = view.findViewById(R.id.placeholder)
        actualResult = view.findViewById(R.id.actual_result)
        return view
    }

    // Function runs in UI Thread and displays the classification statistics to the user
    // and is called right after 'onCreateView()'
    @UiThread
    @SuppressLint("DefaultLocale", "SetTextI18n")
    fun showRecognitionResults(results: List<Recognition?>?, time: Long) {
        if (results != null) {
            placeholder!!.visibility = View.GONE
            actualResult!!.visibility = View.VISIBLE
            latency!!.text = "classifying this frame took $time ms"

            // hide result rows if too few classes are detected
            for (id in results.size until maxResults) {
                rows[id]?.visibility = View.GONE
            }

            for (id in 0 until maxResults) {
                descriptions[id]?.text = results[id]?.title
                confidences[id]?.text = String.format("%.1f", 100 * results[id]?.confidence!!) + "%"
            }

        } else { // hide all result rows
            for (id in 0 until maxResults) {
                rows[id]?.visibility = View.GONE
            }
        }
    }

    companion object {
        fun newInstance(): CameraRollPredictionsFragment {
            return CameraRollPredictionsFragment()
        }
    }
}