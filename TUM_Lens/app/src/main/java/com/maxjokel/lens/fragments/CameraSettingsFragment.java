package com.maxjokel.lens.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.maxjokel.lens.helpers.CameraEvents;
import com.maxjokel.lens.R;

import java.util.ArrayList;
import java.util.List;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
*
* This fragment controls the camera settings in the BottomSheet in the 'ViewFinder' activity
*
* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */


public class CameraSettingsFragment extends Fragment {

    private List<CameraEvents> listeners = new ArrayList<CameraEvents>();

    public CameraSettingsFragment() {
        // Required empty public constructor
    }

    public static CameraSettingsFragment newInstance(String param1, String param2) {
        return new CameraSettingsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        // this is called right after 'onCreateView()'

        // set up button for rotating camera
        getView().findViewById(R.id.btn_rotate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // perform haptic feedback
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);

                // trigger classifier update   [source: https://stackoverflow.com/a/6270150]
                for (CameraEvents events : listeners)
                    events.onRotateToggled();
            }
        });

        // turn flash on or off
        getView().findViewById(R.id.btn_flash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // trigger classifier update   [source: https://stackoverflow.com/a/6270150]
                for (CameraEvents events : listeners)
                    events.onFlashToggled();

            }
        });

    }


    public void addListener(CameraEvents toAdd) {
        listeners.add(toAdd);
    }

}