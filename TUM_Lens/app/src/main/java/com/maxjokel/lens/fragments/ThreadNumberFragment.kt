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
import com.maxjokel.lens.classification.Classifier.Companion.onConfigChanged
import com.maxjokel.lens.R
import java.util.*

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
*
* Fragment that controls the frame number selector in the BottomSheet of the ViewFinder activity
*
* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */
class ThreadNumberFragment  // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// empty public constructor
    : Fragment() {
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // layout elements
    private var tv: TextView? = null
    private var btn_plus: ImageButton? = null
    private var btn_minus: ImageButton? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // instantiate new SharedPreferences object
    var prefs: SharedPreferences? = null
    var prefEditor: SharedPreferences.Editor? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // other global variables
    private var THREADNUMBER = 3 // default is 3

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // load sharedPreferences object and set up editor
        prefs = Objects.requireNonNull(this.activity)!!
            .getSharedPreferences("TUM_Lens_Prefs", Context.MODE_PRIVATE)


        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_thread_number, container, false)

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;
        tv = view.findViewById<View>(R.id.tv_threads) as TextView
        btn_minus = view.findViewById<View>(R.id.btn_threads_minus) as ImageButton
        btn_plus = view.findViewById<View>(R.id.btn_threads_plus) as ImageButton
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // this is called right after 'onCreateView()'

        // init SharedPreferences Editor
        prefEditor = prefs!!.edit()

        // load number of threads from SharedPreferences
        val saved_numberOfThreads = prefs!!.getInt("threads", 0)
        if (saved_numberOfThreads > 0 && saved_numberOfThreads <= 15) {
            THREADNUMBER = saved_numberOfThreads // update if within accepted range
        }
        updateThreadCounter()


        // decrease number of threads
        btn_minus!!.setOnClickListener { v ->
            if (THREADNUMBER > 1) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                THREADNUMBER--

                // update UI and save to SharedPreferences
                updateThreadCounter()

                // trigger classifier update
                onConfigChanged()
            }
        }

        // increase number of threads
        btn_plus!!.setOnClickListener { v ->
            if (THREADNUMBER < 15) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                THREADNUMBER++

                // update UI and save to SharedPreferences
                updateThreadCounter()

                // trigger classifier update
                onConfigChanged()
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
        prefEditor!!.putInt("threads", THREADNUMBER)
        prefEditor!!.apply()

        // update UI
        tv!!.text = "" + THREADNUMBER
    }

    companion object {
        fun newInstance(): ThreadNumberFragment {
            return ThreadNumberFragment()
        }
    }
}