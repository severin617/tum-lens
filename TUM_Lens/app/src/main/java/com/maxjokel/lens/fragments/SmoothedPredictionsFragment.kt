package com.maxjokel.lens.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import com.maxjokel.lens.R
import com.maxjokel.lens.helpers.Recognition
import com.maxjokel.lens.helpers.ResultItem
import com.maxjokel.lens.helpers.ResultItemComparator
import java.util.*

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
*
* Fragment for displaying the classification smoothed results
*
* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */
class SmoothedPredictionsFragment: Fragment() {

    private var placeholder: LinearLayout? = null
    private var actualResult: LinearLayout? = null
    private var desc0: TextView? = null
    private var desc1: TextView? = null
    private var desc2: TextView? = null
    private var desc3: TextView? = null
    private var desc4: TextView? = null
    private var conf0: TextView? = null
    private var conf1: TextView? = null
    private var conf2: TextView? = null
    private var conf3: TextView? = null
    private var conf4: TextView? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // init global variables related to 'showSmoothedRecognitionResults()'
    private var _counter = 0
    var _map: Map<String, ResultItem> = HashMap()
    var _collection: MutableList<Recognition> = LinkedList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_smoothed_predictions, container, false)

        // set up all layout elements here as we will run into NullPointerExceptions when we use
        // the dynamic 'getView()...' approach;
        placeholder = view.findViewById(R.id.placeholder)
        actualResult = view.findViewById(R.id.actual_result)
        desc0 = view.findViewById(R.id.smooth_description0)
        desc1 = view.findViewById(R.id.smooth_description1)
        desc2 = view.findViewById(R.id.smooth_description2)
        desc3 = view.findViewById(R.id.smooth_description3)
        desc4 = view.findViewById(R.id.smooth_description4)
        conf0 = view.findViewById(R.id.smooth_confidence0)
        conf1 = view.findViewById(R.id.smooth_confidence1)
        conf2 = view.findViewById(R.id.smooth_confidence2)
        conf3 = view.findViewById(R.id.smooth_confidence3)
        conf4 = view.findViewById(R.id.smooth_confidence4)
        return view
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // showSmoothedRecognitionResults()
    //
    // Background:
    //   the classification is executed on a 'per frame' basis
    //   this makes it hard to read the actual classification results
    //
    // Idea:
    //   introduce 'artificial latency' into the process of displaying the classification results
    //   by taking an average over the last x classification results
    @UiThread
    @SuppressLint("DefaultLocale", "SetTextI18n")
    fun showSmoothedRecognitionResults(results: List<Recognition?>?) {
        if (_counter <= 9) {

            // IDEA:
            // while the counter is less than 10, add the first three most promising classification results to a list
            if (results != null && results.size > 0) { // if there are results, add them to the list
                val recognition0 = results[0]
                if (recognition0 != null && recognition0.title != null && recognition0.confidence != null) {
                    _collection.add(recognition0)
                }
                val recognition1 = results[1]
                if (recognition1 != null && recognition1.title != null && recognition1.confidence != null) {
                    _collection.add(recognition1)
                }
                val recognition2 = results[2]
                if (recognition2 != null && recognition2.title != null && recognition2.confidence != null) {
                    _collection.add(recognition2)
                }
                val recognition3 = results[3]
                if (recognition3 != null && recognition3.title != null && recognition3.confidence != null) {
                    _collection.add(recognition3)
                }
                val recognition4 = results[4]
                if (recognition4 != null && recognition4.title != null && recognition4.confidence != null) {
                    _collection.add(recognition4)
                }

                // increment counter
                _counter++
            }
        } else { // now, the list should contain ~ 10x5 = 50 elements

            // IDEA
            //  - create a new 'List' data structure, that holds the custom 'ResultItem' objects
            //  - iterate over the last 10 classified frames and group multiple occurring results
            //     - if the new list is empty: just add the element
            //     - else try to find the previously added instance and update it
            //     - else add as a new element

            // new 'List' data structure
            val list: MutableList<ResultItem> = LinkedList()

            // iterate
            for (i in _collection.indices) {
                val a = _collection[i] // improve memory efficiency
                if (list.size == 0) { // if list is empty, just add ResultItem to it
                    val c = ResultItem(a.title!!, a.confidence!!)
                    list.add(c)
                } else { // else, look for previous occurrences
                    var foundIt = false
                    for (j in list.indices) {
                        if (list[j].title === a.title) { // is already element of list

                            // load element from List
                            val b = list[j]

                            // update element
                            b.incOccurrences()
                            b.addToConfidence(a.confidence!!)

                            // put back into List
                            list[j] = b
                            foundIt = true
                        }
                    }
                    if (!foundIt) { // otherwise, just add it to the list
                        val c = ResultItem(a.title!!, a.confidence!!)
                        list.add(c)
                    }
                }
            }

            // STEP 2: sort by number of occurrences and confidence level
            Collections.sort(list, ResultItemComparator())


            // STEP 3: output the first 5 list elements
            if (list.size >= 5) {

                // hide placeholder and show actual results
                placeholder!!.visibility = View.GONE
                actualResult!!.visibility = View.VISIBLE
                val r0 = list[0]
                desc0!!.text = r0.title + " (" + r0.occurrences + "x)"
                conf0!!.text = String.format("%.1f", 100 * r0.confidence / r0.occurrences) + "%"
                val r1 = list[1]
                desc1!!.text = r1.title + " (" + r1.occurrences + "x)"
                conf1!!.text = String.format("%.1f", 100 * r1.confidence / r1.occurrences) + "%"
                val r2 = list[2]
                desc2!!.text = r2.title + " (" + r2.occurrences + "x)"
                conf2!!.text = String.format("%.1f", 100 * r2.confidence / r2.occurrences) + "%"
                val r3 = list[3]
                desc3!!.text = r3.title + " (" + r3.occurrences + "x)"
                conf3!!.text = String.format("%.1f", 100 * r3.confidence / r3.occurrences) + "%"
                val r4 = list[4]
                desc4!!.text = r4.title + " (" + r4.occurrences + "x)"
                conf4!!.text = String.format("%.1f", 100 * r4.confidence / r4.occurrences) + "%"
            }


            // reset data structures for next iteration
            _counter = 0
            _map = HashMap()
            _collection = LinkedList()
        }
    }

    companion object {
        fun newInstance(): SmoothedPredictionsFragment {
            return SmoothedPredictionsFragment()
        }
    }
}