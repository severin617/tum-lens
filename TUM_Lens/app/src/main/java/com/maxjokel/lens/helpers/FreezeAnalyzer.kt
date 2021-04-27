package com.maxjokel.lens.helpers

import android.annotation.SuppressLint
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.maxjokel.lens.helpers.ImageUtils.toBitmap

/* 13.09.2020, 19:45

   We use this custom CameraX Analyzer to display the last camera view frame as 'frozen'
   when the user pauses the classification. While there (were) are several approaches to this,
   this one is apparently the one suggested by the Google developer team.

   IDEA
    - adapted from here: https://stackoverflow.com/a/59674075
    - we use a callback interface to communicate between 'ViewFinder.java' and this class
    - here, an 'event' is fired   [Part 1]
    - it is then processed in 'ViewFinder.java', where we actually freeze the UI   [Part 2] */

class FreezeAnalyzer(private val callback: FreezeCallback) : ImageAnalysis.Analyzer {

    private var isFrozen = false
    private var lens_front_back = 0 // [0 = back, 1 = front]; set 'back' as default;

    override fun analyze(image: ImageProxy) {
        // analyze() does nothing but closing the image;
        // if the 'isFrozen' flag is set, it will return a bitmap within its callback
        if (isFrozen) {
            // convert the CameraX YUV ImageProxy to a (RGB?) bitmap
            @ExperimentalGetImage val img = image.image
            @SuppressLint("UnsafeExperimentalUsageError") val bmp =
                toBitmap(img!!, lens_front_back, image.imageInfo.rotationDegrees)
            // trigger new callback
            callback.onFrozenBitmap(bmp)
            // reset flag
            isFrozen = false
        }
        // close image to continue processing the next frames
        image.close()
    }

    fun freeze(lens: Int) {
        isFrozen = true
        lens_front_back = lens
    }

    companion object {
        private val LOGGER = Logger()
    }
}