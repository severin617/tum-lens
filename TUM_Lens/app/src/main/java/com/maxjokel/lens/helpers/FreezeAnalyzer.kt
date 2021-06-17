package com.maxjokel.lens.helpers

import android.annotation.SuppressLint
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.maxjokel.lens.helpers.ImageUtils.toBitmap

/* We use this custom CameraX Analyzer to display the last camera view frame as 'frozen'
   when the user pauses the classification. While there (were) are several approaches to this,
   this one is apparently the one suggested by the Google developer team.

   IDEA
    - adapted from here: https://stackoverflow.com/a/59674075
    - we use a callback interface to communicate between 'ClassificationActivity' and this class
    - here, an 'event' is fired [Part 1]
    - it is then processed in 'ClassificationActivity' where we actually freeze the UI [Part 2] */

class FreezeAnalyzer(private val callback: FreezeCallback) : ImageAnalysis.Analyzer {

    private var isFrozen = false
    private var lensFrontBack = 0 // [0 = back, 1 = front]; set 'back' as default;

    override fun analyze(imgProxy: ImageProxy) {
        // analyze() does nothing but close the image
        // if the 'isFrozen' flag is set, it will return a bitmap within its callback
        if (isFrozen) {
            // convert the CameraX YUV ImageProxy to a (RGB?) bitmap
            @ExperimentalGetImage val img = imgProxy.image
            @SuppressLint("UnsafeExperimentalUsageError")
            val bmp = toBitmap(img!!, lensFrontBack, imgProxy.imageInfo.rotationDegrees)
            callback.onFrozenBitmap(bmp) // trigger new callback
            isFrozen = false // reset flag
        }
        imgProxy.close() // close image proxy to continue processing the next frames
    }

    fun freeze(lens: Int) {
        isFrozen = true
        lensFrontBack = lens
    }

    companion object {
        private val LOGGER = Logger()
    }
}