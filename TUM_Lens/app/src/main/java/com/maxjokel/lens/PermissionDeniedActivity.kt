package com.maxjokel.lens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    08.07.2020, 15:30

    User is shown this explanatory activity in case the necessary permissions are not granted.

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */
class PermissionDeniedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // set status bar background to black
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)

        // set corresponding layout
        setContentView(R.layout.activity_permission_denied)

        // UI elements of view
        val btn_exit = findViewById<Button>(R.id.buttonExit)
        val btn_settings = findViewById<Button>(R.id.buttonSettings)

        // EXIT the app
        btn_exit.setOnClickListener { // Source: https://developer.android.com/reference/android/app/Activity#finishAffinity()
            finishAffinity()
        }

        // redirect user to SETTINGS
        btn_settings.setOnClickListener { // Source: https://stackoverflow.com/a/32983128
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
    }
}