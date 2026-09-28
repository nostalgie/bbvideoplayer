package com.dima.bbvideoplayer.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

object StoragePermissionHelper {

    fun hasStoragePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager()
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Runtime permissions a dialog can actually grant. On API 30-32 the check
     * requires the All-Files-Access app op, which no dialog can grant - the
     * caller must route to the settings screen ([needsManageAllFilesAccess]).
     */
    fun requiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> emptyArray()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> emptyArray()
        }
    }

    fun needsManageAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
}
