package com.maxjokel.lens;

import java.util.Comparator;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    We use this class for realizing the "smoothed results".

    For each classified frame we obtain a list with the top-n prediction results.
    We store the top-3 items as 'ResultItem' objects in a dedicated list,
    that we sort by the number of occurrences after we the results of x frames.

    For that reason we implement the custom comparator.

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class ResultItem implements Comparable<ResultItem>{

    String title;
    String id;
    Float confidence = 0.0f;
    int occurrences = 0;

    public ResultItem(String t, Float c){
        this.title = t;
        this.confidence = c;
        this.occurrences = 1;
    }

    public String getTitle() {
        return title;
    }

    public String getId(){
        return this.id;
    }

    public void addToConfidence(Float c){
        this.confidence += c;
    }

    public void setConfidence(Float c){
        this.confidence = c;
    }

    public Float getConfidence(){
        return this.confidence;
    }

    public void setOccurrences(int n){
        this.occurrences = n;
    }

    public int getOccurrences(){
        return this.occurrences;
    }

    public void incOccurrences(){
        this.occurrences++;
    }

    @Override
    public int compareTo(ResultItem o) {
        return 0;
    }
}


class ResultItemComparator implements Comparator<ResultItem> {
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