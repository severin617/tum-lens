package com.maxjokel.lens.helpers

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.File

class DownloadFiles(val context: Context) {

    val STORAGE_PERMISSION_CODE : Int = 1000
    var myDownloadId : Long = 0

    fun startDownloading (filename : String, radioButton: RadioButton) {

        val url = "https://www5.in.tum.de/~reiz/osama/$filename"
        Log.d ("URL","the url is $url")

        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        request.setTitle(filename)
//        request.setDescription("The file is downloading")
        request.allowScanningByMediaScanner()
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if(Environment.isExternalStorageLegacy()){
                request.setDestinationInExternalPublicDir("/models", filename)
            } else {
                //request.setDestinationInExternalPublicDir( Environment.DIRECTORY_DOWNLOADS, filename)
                //val path =  context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.path + "/models/" + filename
                val path = "models/$filename"
                request.setDestinationInExternalFilesDir(context, null, path)
            }
        }else {
            request.setDestinationInExternalPublicDir("/models", filename)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        myDownloadId = manager.enqueue(request)

        var br =  object : BroadcastReceiver () {
            override fun onReceive(p0: Context?, p1: Intent?) {
                var id: Long? = p1?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == myDownloadId) {
                    Toast.makeText(context, "Model downloaded successfully", Toast.LENGTH_LONG).show()
                    radioButton.isClickable = true
                }
            }
        }
        context.registerReceiver(br, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }


    fun reqPermission(activity: Activity, filename: String, radioButton: RadioButton) : Boolean {

        if (checkForInternet(context)) {
            Log.d("RequestPermission", "in reqPermission")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED
                ) {
                    Log.d("RequestPermission", "reqPermission")
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        this.STORAGE_PERMISSION_CODE
                    )
                    return false
                } else {
                    Log.d("RequestPermission", "Start downloading")
                    startDownloading(filename, radioButton)
                    return true
                }
            } else {
                Log.d("RequestPermission", "Start downloading")
                startDownloading(filename, radioButton)
                return true
            }
        } else {
            Toast.makeText(context, "No Internet Connection! Cannot download the model!", Toast.LENGTH_LONG).show()
            return false
        }
    }



    private fun checkForInternet(context: Context): Boolean {

        // register activity with the connectivity manager service
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // if the android version is equal to M
        // or greater we need to use the
        // NetworkCapabilities to check what type of
        // network has the internet connection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Returns a Network object corresponding to
            // the currently active default data network.
            val network = connectivityManager.activeNetwork ?: return false

            // Representation of the capabilities of an active network.
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                // Indicates this network uses a Wi-Fi transport,
                // or WiFi has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

                // Indicates this network uses a Cellular transport. or
                // Cellular has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true

                // else return false
                else -> false
            }
        } else {
            // if the android version is below M
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

}
