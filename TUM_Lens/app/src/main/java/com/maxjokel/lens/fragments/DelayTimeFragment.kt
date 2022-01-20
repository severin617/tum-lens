package com.maxjokel.lens.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.maxjokel.lens.R
import com.maxjokel.lens.classification.Classifier

class DelayTimeFragment : Fragment () {

    private lateinit var delayText: TextView
    private lateinit var delayButton: Button
    private lateinit var delayEditText: EditText

    var prefs: SharedPreferences? = null
    var prefEditor: SharedPreferences.Editor? = null

    private var delayTime = 3

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        // load sharedPreferences object and set up editor
        prefs = this.requireActivity()
            .getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)

        val view = inflater.inflate(R.layout.fragment_delay_time, container, false)

        delayText = view.findViewById(R.id.delay_text)
        delayButton = view.findViewById(R.id.delay_time_button)
        delayEditText = view.findViewById(R.id.delay_time_edit_text)
        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefEditor = prefs!!.edit()

        val savedDelayTime = prefs!!.getInt("delay", 0)
        delayTime = savedDelayTime

        delayText.text = "delay time is $delayTime seconds"

        delayButton.setOnClickListener(View.OnClickListener{
            if (delayEditText.text.isNotEmpty()) {
                delayTime = Integer.parseInt(delayEditText.text.toString())
                delayEditText.text.clear()
                updateDelayTime()
                Classifier.onConfigChanged()
            } else {
                Toast.makeText(context, "You did not enter the delay time needed!", Toast.LENGTH_SHORT).show()
            }
        })
    }


    @SuppressLint("SetTextI18n")
    protected fun updateDelayTime() {

        // save number of threads to sharedPreferences
        prefEditor!!.putInt("delay", delayTime)
        prefEditor!!.apply()

        // update UI
        delayText.text = "delay time is $delayTime seconds"
    }

    companion object {
        fun newInstance(): ThreadNumberFragment {
            return ThreadNumberFragment()
        }
    }
}