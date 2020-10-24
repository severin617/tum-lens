package com.maxjokel.lens.helpers;

import java.util.Comparator;

public class ResultItemComparator implements Comparator<ResultItem> {
    @Override
    public int compare(ResultItem r1, ResultItem r2) {

        // IDEA:
        // if the number of 'occurrences' is equal, then take a look at 'confidence'

        // TODO
        // this approach uses the 'confidence sum'
        // this might affect the displayed sort order, as we divide the sum by the number of occurrences...

        int t = r2.getOccurrences() - r1.getOccurrences();

        if(t == 0){ // occurrences do not differ

            if (r1.getConfidence() > r2.getConfidence()) return -1;
            if (r1.getConfidence() < r2.getConfidence()) return 1;
            return 0;

        } else { // occurrences differ
            return t;
        }

    }
}