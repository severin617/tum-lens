package com.maxjokel.lens.helpers

import android.graphics.Bitmap

// new callback interface, to react to changes in 'viewFinder.java'
// [reference: https://stackoverflow.com/a/18054865]
interface FreezeCallback {
    fun onFrozenBitmap(b: Bitmap?)
}