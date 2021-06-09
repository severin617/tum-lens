package com.maxjokel.lens.helpers

import android.content.ContentResolver
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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

    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    private const val kMaxChannelValue = 262143

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

        // crop to square
        var temp1 = if (bitmap.width < bitmap.height) {
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

    // << utils for object detection package >>

    /**
     * Utility method to compute the allocated size in bytes of a YUV420SP image of the given
     * dimensions.
     */
    @JvmStatic
    fun getYUVByteSize(width: Int, height: Int): Int {
        // The luminance plane requires 1 byte per pixel.
        val ySize = width * height

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        val uvSize = (width + 1) / 2 * ((height + 1) / 2) * 2
        return ySize + uvSize
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     * @param filename The location to save the bitmap to.
     */
    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     */
    @JvmOverloads
    fun saveBitmap(bitmap: Bitmap, filename: String = "preview.png") {
        val root =
            Environment.getExternalStorageDirectory().absolutePath + File.separator + "tensorflow"
        LOGGER.i("Saving %dx%d bitmap to %s.", bitmap.width, bitmap.height, root)
        val myDir = File(root)
        if (!myDir.mkdirs()) {
            LOGGER.i("Make dir failed")
        }
        val file = File(myDir, filename)
        if (file.exists()) {
            file.delete()
        }
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            LOGGER.e(e, "Exception!")
        }
    }

    @JvmStatic
    fun convertYUV420ToARGB8888(
        yData: ByteArray,
        uData: ByteArray,
        vData: ByteArray,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        out: IntArray
    ) {
        var yp = 0
        for (j in 0 until height) {
            val pY = yRowStride * j
            val pUV = uvRowStride * (j shr 1)
            for (i in 0 until width) {
                val uv_offset = pUV + (i shr 1) * uvPixelStride
                out[yp++] = YUV2RGB(
                    0xff and yData[pY + i].toInt(), 0xff and uData[uv_offset]
                        .toInt(), 0xff and vData[uv_offset].toInt()
                )
            }
        }
    }

    @JvmStatic
    fun convertYUV420SPToARGB8888(input: ByteArray, width: Int, height: Int, output: IntArray) {
        val frameSize = width * height
        var j = 0
        var yp = 0
        while (j < height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0
            var i = 0
            while (i < width) {
                val y = 0xff and input[yp].toInt()
                if (i and 1 == 0) {
                    v = 0xff and input[uvp++].toInt()
                    u = 0xff and input[uvp++].toInt()
                }
                output[yp] = YUV2RGB(y, u, v)
                i++
                yp++
            }
            j++
        }
    }

    private fun YUV2RGB(y: Int, u: Int, v: Int): Int {
        // Adjust and check YUV values
        var y = y
        var u = u
        var v = v
        y = if (y - 16 < 0) 0 else y - 16
        u -= 128
        v -= 128

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);
        val y1192 = 1192 * y
        var r = y1192 + 1634 * v
        var g = y1192 - 833 * v - 400 * u
        var b = y1192 + 2066 * u

        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = if (r > kMaxChannelValue) kMaxChannelValue else if (r < 0) 0 else r
        g = if (g > kMaxChannelValue) kMaxChannelValue else if (g < 0) 0 else g
        b = if (b > kMaxChannelValue) kMaxChannelValue else if (b < 0) 0 else b
        return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
    }

    /**
     * Returns a transformation matrix from one reference frame into another. Handles cropping (if
     * maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth Width of source frame.
     * @param srcHeight Height of source frame.
     * @param dstWidth Width of destination frame.
     * @param dstHeight Height of destination frame.
     * @param applyRotation Amount of rotation to apply from one frame to another. Must be a multiple
     * of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     * cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    @JvmStatic
    fun getTransformationMatrix(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
                                applyRotation: Int, maintainAspectRatio: Boolean): Matrix {
        val matrix = Matrix()
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", applyRotation)
            }
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)
            // Rotate around origin.
            matrix.postRotate(applyRotation.toFloat())
        }
        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        val transpose = (Math.abs(applyRotation) + 90) % 180 == 0
        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()
            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                val scaleFactor = Math.max(scaleFactorX, scaleFactorY)
                matrix.postScale(scaleFactor, scaleFactor)
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY)
            }
        }
        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }
        return matrix
    }
}