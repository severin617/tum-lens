package com.maxjokel.lens.helpers

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import java.lang.ref.WeakReference

// in order to access the '/assets' directory from 'StaticClassifier', we need a 'Context' object;
// we use this workaround based on [https://stackoverflow.com/a/4391811] to encounter this;
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        mContext = WeakReference(this)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var mContext: WeakReference<Context>? = null
        @JvmStatic
        val context: Context?
            get() = mContext!!.get()
    }
}