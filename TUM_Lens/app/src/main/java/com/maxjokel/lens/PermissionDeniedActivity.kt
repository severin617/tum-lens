package com.maxjokel.lens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// User is shown this explanatory activity in case the necessary permissions are not granted.
class PermissionDeniedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        setContentView(R.layout.activity_permission_denied)

        val btnExit = findViewById<Button>(R.id.buttonExit)
        val btnSettings = findViewById<Button>(R.id.buttonSettings)

        btnExit.setOnClickListener { finishAffinity() }

        // redirect user to SETTINGS (source: https://stackoverflow.com/a/32983128)
        btnSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        }
    }
}