package com.maxjokel.lens;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import java.lang.ref.WeakReference;

// in order to access the '/assets' directory from 'StaticClassifier', we need a 'Context' object;
// we use this workaround based on [https://stackoverflow.com/a/4391811] to encounter this;

public class App extends Application {

    @SuppressLint("StaticFieldLeak")
    private static WeakReference<Context> mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = new WeakReference<Context>(this);
    }

    public static Context getContext(){
        return mContext.get();
    }
}