package com.maxjokel.lens.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.maxjokel.lens.R
import com.maxjokel.lens.classification.Classifier

/* Fragment that controls the frame number selector in the BottomSheet of the ViewFinder activity */
class ThreadNumberFragment : Fragment() {

    private lateinit var tv: TextView
    private lateinit var btnPlus: ImageButton
    private lateinit var btnMinus: ImageButton

    var prefs: SharedPreferences? = null
    var prefEditor: SharedPreferences.Editor? = null

    private var threadNumber = 3

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load sharedPreferences object and set up editor
        prefs = this.requireActivity()
            .getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)

        val view = inflater.inflate(R.layout.fragment_thread_number, container, false)

        tv = view.findViewById(R.id.tv_threads)
        btnMinus = view.findViewById(R.id.btn_threads_minus)
        btnPlus = view.findViewById(R.id.btn_threads_plus)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // this is called right after 'onCreateView()'

        // init SharedPreferences Editor
        prefEditor = prefs!!.edit()

        // load number of threads from SharedPreferences
        val savedNumberOfThreads = prefs!!.getInt("threads", 0)
        if (savedNumberOfThreads > 0 && savedNumberOfThreads <= 15) {
            threadNumber = savedNumberOfThreads // update if within accepted range
        }
        updateThreadCounter()

        // decrease number of threads
        btnMinus.setOnClickListener { v ->
            if (threadNumber > 1) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                threadNumber--
                updateThreadCounter() // update UI and save to SharedPreferences
                Classifier.onConfigChanged() // trigger classifier update
            }
        }
        // increase number of threads
        btnPlus.setOnClickListener { v ->
            if (threadNumber < 15) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                threadNumber++
                updateThreadCounter() // update UI and save to SharedPreferences
                Classifier.onConfigChanged() // trigger classifier update
            }
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // updateThreadCounter()
    //
    //   - updates component in BottomSheet accordingly
    //   - saves number of threads to SharedPreferences object
    @SuppressLint("SetTextI18n")
    protected fun updateThreadCounter() {

        // save number of threads to sharedPreferences
        prefEditor!!.putInt("threads", threadNumber)
        prefEditor!!.apply()

        // update UI
        tv.text = "" + threadNumber
    }

    companion object {
        fun newInstance(): ThreadNumberFragment {
            return ThreadNumberFragment()
        }
    }
}