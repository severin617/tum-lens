package com.maxjokel.lens.helpers

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File

class DownloadFiles(val context: Context) {

    val STORAGE_PERMISSION_CODE : Int = 1000

    fun startDownloading (filename : String) {
        val url = "https://www5.in.tum.de/~reiz/osama/$filename"
        Log.d ("URL","the url is $url")

        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        request.setTitle(filename)
//        request.setDescription("The file is downloading")
        request.allowScanningByMediaScanner()
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir("/models", filename)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)

    }

    fun reqPermission(activity: Activity, filename: String) {
        Log.d("RequestPermission","in reqPermission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED) {
                Log.d("RequestPermission","reqPermission")
                ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    this.STORAGE_PERMISSION_CODE)
            }
            else {
                Log.d("RequestPermission","Start downloading")
                startDownloading(filename)
            }
        }
        else {
            Log.d("RequestPermission","Start downloading")
            startDownloading(filename)
        }
    }


}
