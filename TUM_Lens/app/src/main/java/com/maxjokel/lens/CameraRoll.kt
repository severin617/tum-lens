package com.maxjokel.lens

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.maxjokel.lens.fragments.CameraRollPredictionsFragment
import com.maxjokel.lens.fragments.ModelSelectorFragment
import com.maxjokel.lens.helpers.ImageUtils
import com.maxjokel.lens.helpers.Logger
import java.io.IOException


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    this activity lets you pick an image from camera roll and will classify it
    ---
    adapted from here: https://stackoverflow.com/a/2636538 (later converted to Kotlin)
+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

class CameraRoll : AppCompatActivity() {
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // holds the last image, so that when the model is changed we can re-run classification
    private var savedBitmap: Bitmap? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // instantiate new SharedPreferences object
    var prefs: SharedPreferences? = null

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // Fragment in BottomSheet that displays the classification results
    lateinit var predictionsFragment: CameraRollPredictionsFragment
    private var _isInitialCall = true

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // load sharedPreferences object and set up editor
        prefs = getSharedPreferences("TUM_Lens_Prefs", MODE_PRIVATE)

        // prevent display from being dimmed down
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // set status bar background to black
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)

        // set corresponding layout
        setContentView(R.layout.activity_camera_roll)

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +             SETUP BOTTOM SHEET FRAGMENTS              +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // init new Fragment Instances
        val msf = ModelSelectorFragment.newInstance()
        predictionsFragment = CameraRollPredictionsFragment.newInstance()
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.add(R.id.modelselector_container, msf, "msf")
        fragmentTransaction.add(R.id.results_container, predictionsFragment, "predictionsFragment")
        fragmentTransaction.commit()

        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
        // +                SET UP EVENT LISTENERS                 +
        // + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

        // bind event listener to button; open camera roll
        findViewById<View>(R.id.ll_pick_image).setOnClickListener { v ->
            if (_isInitialCall) {

                // perform haptic feedback
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)

                // in onCreate or any event where your want the user to select a file
                val intent = Intent()
                intent.type = "image/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startActivityForResult(
                    Intent.createChooser(intent, "Select Picture"),
                    SELECT_PICTURE
                )
            }
        }
        findViewById<View>(R.id.btn_start_over).setOnClickListener { v -> // perform haptic feedback
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)

            // in onCreate or any event where your want the user to select a file
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE)
        }
    }

    // onActivityResult() is called once an image from camera roll is selected
    // fetches image from disk and transforms it to bitmap
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            val selectedImageUri = data!!.data

            // load image as bitmap
            var bmp: Bitmap? = selectedImageUri?.let { ImageUtils.loadFromUri(it, contentResolver) }

            // display, then crop and classify
            if (bmp != null) {

                // render to screen
                val iv = findViewById<ImageView>(R.id.bmp_camera_roll)
                iv.setImageBitmap(bmp)

                // use helper class to crop the bitmap to square
                val croppedBitmap = ImageUtils.cropBitmap(bmp)
                savedBitmap = croppedBitmap

                // start classification
                try {
                    classify(croppedBitmap)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            } else {
                LOGGER.e("Error occurred while processing bitmap in CameraRoll: bitmap is null!")
            }

            // adjust UI: show button to select another image
            if (_isInitialCall) {
                _isInitialCall = false
                findViewById<View>(R.id.btn_start_over).visibility = View.VISIBLE
            }
        }
    }

    @Throws(InterruptedException::class)
    fun classify(bitmap: Bitmap?) {
        val startTime = SystemClock.uptimeMillis()

        // run inference on image
        val results = Classifier.recognizeImage(bitmap)

//        LOGGER.i("RESULT 0: " + results.get(0));
//        LOGGER.i("RESULT 1: " + results.get(1));
//        LOGGER.i("RESULT 2: " + results.get(2));
//        LOGGER.i("RESULT 3: " + results.get(3));
//        LOGGER.i("RESULT 4: " + results.get(4));
        val startTimestamp = SystemClock.uptimeMillis() - startTime
        runOnUiThread { // pass list to fragment, that renders the recognition results to UI
            predictionsFragment.showRecognitionResults(results, startTimestamp)
        }
    }

    // intercept 'back button pressed' event
    override fun onBackPressed() {

        // reset saved bitmap
        savedBitmap = null

        // launch view finder activity
        val intent = Intent(this@CameraRoll, ViewFinder::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        // init new Logger instance
        private val LOGGER = Logger()

        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
        // this is the action code we use in our intent,
        // this way we know we're looking at the response from our own action
        private const val SELECT_PICTURE = 1
    }
}