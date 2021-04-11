package com.maxjokel.lens.helpers

import android.content.ContentResolver
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +
    static helper class that holds methods for image retrieval and transformation
+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

object ImageUtils {
    // init new Logger instance
    private val LOGGER = Logger()

    // width of the cropped square region based on the smaller dimension of the input
    private const val FACTOR = 0.9

    // compression quality
    private const val QUALITY = 35

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // toCroppedBitmap() Method
    // converts a passed Image to RGB Bitmap; used in 'ViewFinder' activity;
    //
    // Workflow
    //  - converts YUV image to RGB bitmap:
    //  - reduces quality to 35%
    //  - crops to square (length is determined by shortest side of image)
    //
    // Findings
    //  - "quality" doesn't seem to have a huge effect on processing time:
    //    both, 25% and 50% run in sub-20ms time frames
    //  - no real effect on prediction accuracy noticeable
    //
    @JvmStatic
    fun toCroppedBitmap(image: Image, rotation: Int): Bitmap {
        return converter(image, 35, true, rotation)
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // toBitmap() Method
    // converts a passed Image to RGB Bitmap (without cropping, scaling); used in 'FreezeAnalyzer';
    //
    // Workflow
    //  - converts YUV image to RGB bitmap:
    //
    @JvmStatic
    fun toBitmap(image: Image, lens_front_back: Int, rotation: Int): Bitmap {
        val temp = converter(image, 100, false, rotation)

        // WORKAROUND
        // on Pixel 3a it is necessary to mirror the image when using the front facing camera...
        val matrix = Matrix()
        if (lens_front_back != 0) {
            matrix.preScale(-1.0f, 1.0f)
        }
        return Bitmap.createBitmap(temp, 0, 0, temp.width, temp.height, matrix, true)
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // converter() Method
    // "backed" for the methods above
    //
    // Workflow
    //  - convert YUV image to RGB bitmap:
    //  - reduce quality as specified in argument
    //  - crops to square (length is determined by shortest side of image)
    //
    private fun converter(image: Image, quality: Int, cropSquare: Boolean, rotation: Int): Bitmap {
        var quality = quality
        if (quality > 100 || quality < 1) {
            quality = QUALITY
        }

        // LOGGER.d("before convert + crop: " + image.getWidth() + "px x " + image.getHeight() + "px");

        // convert YUV image to RGB bitmap and crop   [source: https://stackoverflow.com/a/58568495]
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()

        // create new Rect for cropping; by default full width and height
        val rect = Rect(0, 0, image.width, image.height)
        if (cropSquare) { // if crop is set to true

            // IDEA: the object of desire is probably in the image center;
            // so it should not matter, if we loose some pixels near the bezels
            // -> factor is currently 90% of the smaller side
            val cropSize = (FACTOR * Math.min(image.width, image.height)).toInt()

            // calc offsets for cropping
            val offShortSide = (0.5 * (Math.min(image.width, image.height) - cropSize)).toInt()
            val offLongSide = (0.5 * (Math.max(image.width, image.height) - cropSize)).toInt()

            // set up crop
            if (image.width < image.height) {
                // PORTRAIT
                Rect(
                    offShortSide,  // left
                    offLongSide,  // top
                    image.width - offShortSide,  // right
                    image.height - offLongSide
                ) // bottom
            } else {
                // LANDSCAPE
                Rect(
                    offLongSide,
                    offShortSide,
                    image.width - offLongSide,
                    image.height - offShortSide
                )
            }
        }

        // convert to RGB
        yuvImage.compressToJpeg(rect, quality, out)

        // finally, create bitmap
        val imageBytes = out.toByteArray()
        val temp1 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // LOGGER.d("after convert + crop: " + temp1.getWidth() + "px x " + temp1.getHeight() + "px");

        // rotate image by passed value
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        return Bitmap.createBitmap(temp1, 0, 0, temp1.width, temp1.height, matrix, true)
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // cropBitmap() Method
    // crops passed Bitmap to square; used in 'CameraRoll' activity
    //
    // Workflow
    //  - reduces quality to 35%
    //  - crops to square (length is determined by shortest side of image)
    //
    fun cropBitmap(bitmap: Bitmap): Bitmap {

        // IDEA: the object of desire is probably in the image center;
        // so it should not matter, if we loose some pixels near the bezels
        // -> factor is currently 90% of the smaller side
        val cropSize = (FACTOR * Math.min(bitmap.width, bitmap.height)).toInt()

        // calc offsets for cropping
        val offShortSide = (0.5 * (Math.min(bitmap.width, bitmap.height) - cropSize)).toInt()
        val offLongSide = (0.5 * (Math.max(bitmap.width, bitmap.height) - cropSize)).toInt()
        var temp1: Bitmap? = null

        // crop to square
        temp1 = if (bitmap.width < bitmap.height) {
            // PORTRAIT
            Bitmap.createBitmap(
                bitmap,
                offShortSide,  // left
                offLongSide,  // top
                bitmap.width - offShortSide,  // right
                bitmap.height - offLongSide
            ) // bottom
        } else {
            // LANDSCAPE
            Bitmap.createBitmap(
                bitmap,
                offLongSide,
                offShortSide,
                bitmap.width - offLongSide,
                bitmap.height - offShortSide
            )
        }

        // compress, quality is set to 35%
        val out = ByteArrayOutputStream()
        temp1.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        val imageBytes = out.toByteArray()
        val temp2 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        return Bitmap.createBitmap(temp2, 0, 0, temp2.width, temp2.height)
    }

    fun loadFromUri(photoUri: Uri, contentResolver: ContentResolver): Bitmap? {
        var bmp: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT > 27) {
                val source: ImageDecoder.Source =
                    ImageDecoder.createSource(contentResolver, photoUri)
                bmp = ImageDecoder.decodeBitmap(source)
            } else {
                // support older versions of Android by using getBitmap
                bmp = MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        // read exif info to handle image rotation correctly
        //      [adapted from here: https://stackoverflow.com/a/4105966]
        //      [and here:          https://stackoverflow.com/a/42937272]
        try {
            val inputStream = contentResolver.openInputStream(photoUri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val matrix = Matrix()
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                matrix.postRotate(90f)
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                matrix.postRotate(180f)
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                matrix.postRotate(270f)
            }
            bmp = Bitmap.createBitmap(bmp!!, 0, 0, bmp.width, bmp.height, matrix, true)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bmp
    }
}