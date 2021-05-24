package com.maxjokel.lens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// This is the first activity that is shown to the user. Asks for permissions (camera, etc.) and
// states reasons why they are necessary.
class StartScreen : AppCompatActivity() {
    // init global permission related variables
    private val ALL_PERMISSIONS = 101
    private val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {

        // switch back to default theme [source: https://android.jlelse.eu/the-complete-android-splash-screen-guide-c7db82bce565]
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // prevent display from being dimmed down
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // set status bar background to black
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)

        // set corresponding layout
        setContentView(R.layout.activity_start_screen)

        // check if permissions are already granted
        // if so, then launch directly into the 'ViewFinder' activity
        checkForPermissions()

        // no permission, show the corresponding view...

        // UI elements of view
        val btn_requestPermission = findViewById<Button>(R.id.button)

        // set on-click-listener for button to request permission
        btn_requestPermission.setOnClickListener {
            // check permissions status:
            if (ContextCompat.checkSelfPermission(this@StartScreen,
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                &&
                ContextCompat.checkSelfPermission(this@StartScreen,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

                // permissions granted -> open 'ViewFinder' activity
                val intent = Intent(this@StartScreen, ViewFinder::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(intent)
            } else {
                // request permissions as they haven't been granted yet
                ActivityCompat.requestPermissions(this@StartScreen, permissions, ALL_PERMISSIONS)
            }
        }
    }

    // this function checks if the necessary permissions are already granted
    // if so, it then launches the view-finder activity
    private fun checkForPermissions() {
        // check permissions status:
        if (ContextCompat.checkSelfPermission(this@StartScreen,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            &&
            ContextCompat.checkSelfPermission(this@StartScreen,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            // permissions granted -> open 'ViewFinder' activity
            val intent = Intent(this@StartScreen, ViewFinder::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(intent)
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == ALL_PERMISSIONS) {
            // now verify if both permissions were granted:
            var isGrantedForAll = true
            if (grantResults.size > 0 && permissions.size == grantResults.size) {
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        isGrantedForAll = false
                        break
                    }
                }
            }
            if (isGrantedForAll) {
                // permissions granted -> open 'ViewFinder' activity
                val intent = Intent(this@StartScreen, ViewFinder::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(intent)
                finish()
            } else { // permissions denied
                // switch to view that explains again and offers a redirect to settings
                val intent = Intent(this@StartScreen, PermissionDenied::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(intent)
                finish()
            }
        }
    }
}