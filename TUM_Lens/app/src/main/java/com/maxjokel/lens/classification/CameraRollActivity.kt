package com.maxjokel.lens.classification

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.maxjokel.lens.R
import com.maxjokel.lens.fragments.CameraRollPredictionsFragment
import com.maxjokel.lens.fragments.ModelSelectorFragment
import com.maxjokel.lens.helpers.ImageUtils
import com.maxjokel.lens.helpers.Logger


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    this activity lets you pick an image from camera roll and will classify it
    ---
    adapted from here: https://stackoverflow.com/a/2636538 (later converted to Kotlin)
+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

class CameraRollActivity : AppCompatActivity() {
    // holds the last image, so that when the model is changed we can re-run classification
    private var savedBitmap: Bitmap? = null
    // Fragment in BottomSheet that displays the classification results
    private lateinit var predictionsFragment: CameraRollPredictionsFragment

    private var prefs: SharedPreferences? = null
    private var _isInitialCall = true

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
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)

                // in onCreate or any event where your want the user to select a file
                val intent = Intent()
                intent.type = "image/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startActivityForResult(
                    Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE
                )
            }
        }
        findViewById<View>(R.id.btn_start_over).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)

            // in onCreate or any event where your want the user to select a file
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(
                Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE
            )
        }
    }

    // onActivityResult() is called once an image from camera roll is selected
    // fetches image from disk and transforms it to bitmap
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            val selectedImageUri = data!!.data

            // load image as bitmap
            val bmp: Bitmap? = selectedImageUri?.let { ImageUtils.loadFromUri(it, contentResolver) }

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
        val results = Classifier.recognizeImage(bitmap) // run inference on image
        val startTimestamp = SystemClock.uptimeMillis() - startTime
        runOnUiThread { // pass list to fragment, that renders the recognition results to UI
            predictionsFragment.showRecognitionResults(results, startTimestamp)
        }
    }

    override fun onBackPressed() {
        savedBitmap = null // reset saved bitmap
        val intent = Intent(this@CameraRollActivity, ClassificationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        // TODO: Investigate back stack behaviour; seems like it's doing weird navigation
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