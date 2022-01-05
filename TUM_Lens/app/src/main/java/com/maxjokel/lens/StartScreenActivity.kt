package com.maxjokel.lens

import android.Manifest.permission
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.maxjokel.lens.classification.ClassificationActivity
import com.maxjokel.lens.helpers.Logger
import java.io.File

// This is the first activity that is shown to the user. Asks for permissions (camera, etc.) and
// states reasons why they are necessary.
class StartScreenActivity : AppCompatActivity() {

    private val permissions = arrayOf(permission.CAMERA, permission.READ_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {

        val root = Environment.getExternalStorageDirectory()
        val myDir = File("$root/models")   //sdcard/models

        if (!myDir.exists()) {
            myDir.mkdirs()
        }
        Log.d("Directory", myDir.toString())

        // we only jump into this activity when permissions are still missing
        if (areAllPermissionsGranted()) launchWithoutHistory(ClassificationActivity::class.java)
        // replace splash screen placeholder with app theme once this activity is fully loaded
        // (source: https://android.jlelse.eu/the-complete-android-splash-screen-guide-c7db82bce565)
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        setContentView(R.layout.activity_start_screen)

        findViewById<Button>(R.id.givePermissionBtn).setOnClickListener {
            ActivityCompat.requestPermissions(this, permissions, ALL_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == ALL_PERMISSIONS) {
            // now verify if both permissions were granted:
            if (grantResults.isNotEmpty() && permissions.size == grantResults.size) {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    launchWithoutHistory(ClassificationActivity::class.java)
                } else { // switch to view that explains again and offers a redirect to settings
                    launchWithoutHistory(PermissionDeniedActivity::class.java)
                }
            }
        }
    }
    
    private fun launchWithoutHistory(activity:	Class<*>) {
        val intent = Intent(this@StartScreenActivity, activity)
        startActivity(intent)
        finish()
    }

    private fun areAllPermissionsGranted(): Boolean {
        return isGranted(permission.CAMERA) && isGranted(permission.READ_EXTERNAL_STORAGE)
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this@StartScreenActivity, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val ALL_PERMISSIONS = 101
    }
}