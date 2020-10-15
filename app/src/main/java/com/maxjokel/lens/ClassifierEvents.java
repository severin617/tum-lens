package com.maxjokel.lens;

import android.app.Activity;

import java.io.IOException;

public interface ClassifierEvents {
    void onClassifierConfigChanged(Activity activity) throws IOException;
}
