package com.maxjokel.lens.helpers

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    We use this class for realizing the "smoothed results".

    For each classified frame we obtain a list with the top-n prediction results.
    We store the top-3 items as 'ResultItem' objects in a dedicated list,
    that we sort by the number of occurrences after we the results of x frames.

    For that reason we implement the custom comparator.

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */
class ResultItem(var title: String, c: Float) : Comparable<ResultItem?> {
    var id: String? = null
    var confidence = 0.0f
    var occurrences = 0
    fun addToConfidence(c: Float) {
        confidence += c
    }

    fun incOccurrences() {
        occurrences++
    }

    override fun compareTo(o: ResultItem?): Int {
        return 0
    }

    init {
        confidence = c
        occurrences = 1
    }
}