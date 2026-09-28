package com.dima.bbvideoplayer.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

object HuaweiStorageHelper {

    private const val TAG = "HuaweiStorageHelper"

    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("huawei") || manufacturer.contains("honor")
    }

    fun getAvailableStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()

        try {
            val internalPath = Environment.getExternalStorageDirectory()?.absolutePath
            if (internalPath != null) {
                volumes.add(
                    StorageVolumeInfo(
                        name = "Внутренняя память",
                        path = internalPath,
                        isRemovable = false,
                        isAccessible = File(internalPath).canRead()
                    )
                )
            }

            // StorageManager enumerates the real volumes on every device -
            // including Huawei/Honor, where cards mount at /storage/XXXX-XXXX.
            // The old hardcoded path list (/storage/sdcard1, /storage/sdcard0,
            // /storage/999F-16F3) missed real cards and listed internal
            // emulated storage (sdcard0) as an SD card.
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE)
                as? android.os.storage.StorageManager
            if (storageManager != null) {
                for (volume in storageManager.storageVolumes) {
                    if (!volume.isRemovable) continue
                    val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory?.absolutePath
                    } else {
                        volume.uuid?.let { uuid -> "/storage/$uuid" }
                    }
                    if (path != null && File(path).exists()) {
                        volumes.add(
                            StorageVolumeInfo(
                                name = "SD-карта",
                                path = path,
                                isRemovable = true,
                                isAccessible = File(path).canRead()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage volumes: ${e.message}")
        }

        return volumes
    }

    data class StorageVolumeInfo(
        val name: String,
        val path: String,
        val isRemovable: Boolean,
        val isAccessible: Boolean
    )
}
