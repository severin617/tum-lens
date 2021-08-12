package com.maxjokel.lens.helpers

import android.graphics.RectF

// Result returned by a Classifier or Detector describing what was recognized.
class Recognition(val id: String?, val title: String?, val confidence: Float?,
                  var location: RectF?) {

    /*
    id:          Unique id for what has been recognized. Specific to the class, not the instance of the object.
    title:       Display name for the recognition.
    confidence:  Sortable score for how good the recognition is relative to others. Higher = better.
    location:    Optional location within the source image for the location of the recognized object. */

    override fun toString(): String {
        var resultString = ""
        if (id != null) resultString += "[$id] "
        if (title != null) resultString += "$title "
        if (confidence != null) resultString += String.format("(%.1f%%) ", confidence * 100.0f)
        if (location != null) resultString += "$location "
        return resultString.trim { it <= ' ' }
    }
}