package com.maxjokel.lens.fragments

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.maxjokel.lens.R
import com.maxjokel.lens.helpers.CameraEvents
import java.util.*

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
* This fragment controls the camera settings in the BottomSheet in the 'ViewFinder' activity
* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */
class CameraSettingsFragment : Fragment() {

    private val listeners: MutableList<CameraEvents> = ArrayList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // set up button for rotating camera
        requireView().findViewById<View>(R.id.btn_rotate).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            // trigger classifier update   [source: https://stackoverflow.com/a/6270150]
            for (events in listeners) events.onRotateToggled()
        }
        // turn flash on or off
        requireView().findViewById<View>(R.id.btn_flash).setOnClickListener {
            for (events in listeners) events.onFlashToggled()
        }
    }

    fun addListener(toAdd: CameraEvents) {
        listeners.add(toAdd)
    }
}