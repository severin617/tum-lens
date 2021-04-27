package com.maxjokel.lens.helpers

import java.util.*

class ResultItemComparator : Comparator<ResultItem> {

    override fun compare(r1: ResultItem, r2: ResultItem): Int {
        // IDEA: if the number of 'occurrences' is equal, then take a look at 'confidence'
        // TODO: this approach uses the 'confidence sum' and this might affect the
        //       displayed sort order, as we divide the sum by the number of occurrences...
        val t = r2.occurrences - r1.occurrences
        if (t == 0) { // occurrences do not differ
            if (r1.confidence > r2.confidence) return -1
            if (r1.confidence < r2.confidence) return 1 else return 0
        } else { // occurrences differ
            return t
        }
    }
}