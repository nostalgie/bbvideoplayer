package com.dima.bbvideoplayer.ui.screens.filepicker

import android.content.Context
import com.dima.bbvideoplayer.utils.HuaweiStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap

val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp",
    "m4v", "ts", "mpg", "mpeg", "rmvb", "vob"
)

const val STORAGE_ROOT = "/storage"
const val INTERNAL_STORAGE_PATH = "/storage/emulated/0"

private const val SCAN_CACHE_TTL_MS = 5 * 60 * 1000L

private val directoryScanCache = ConcurrentHashMap<String, DirectoryScanResult>()

data class DirectoryScanResult(
    val files: List<FileSystemItem>,
    val videoCount: Int,
    val lastScanTime: Long
)

private fun getCachedDirectoryScan(dirPath: String): DirectoryScanResult? {
    return directoryScanCache[dirPath]?.takeIf { result ->
        System.currentTimeMillis() - result.lastScanTime < SCAN_CACHE_TTL_MS
    }
}

private fun cacheDirectoryScan(dirPath: String, files: List<FileSystemItem>, videoCount: Int) {
    val now = System.currentTimeMillis()
    // Evict expired entries instead of letting the cache grow unbounded
    // over a kiosk session that runs for days.
    directoryScanCache.entries.removeIf {
        now - it.value.lastScanTime >= SCAN_CACHE_TTL_MS
    }
    directoryScanCache[dirPath] = DirectoryScanResult(
        files = files,
        videoCount = videoCount,
        lastScanTime = now
    )
}

data class StorageVolume(
    val name: String,
    val path: String,
    val isRemovable: Boolean
)

fun listStorageVolumes(context: Context?): List<StorageVolume> {
    val volumes = mutableListOf<StorageVolume>()
    val seenPaths = mutableSetOf<String>()

    fun addVolume(name: String, path: String, isRemovable: Boolean) {
        if (path in seenPaths) return
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return
        seenPaths.add(path)
        volumes.add(StorageVolume(name = name, path = path, isRemovable = isRemovable))
    }

    val isHuawei = context != null && HuaweiStorageHelper.isHuaweiDevice()

    if (isHuawei && context != null) {
        for (volume in HuaweiStorageHelper.getAvailableStorageVolumes(context)) {
            if (volume.isAccessible) {
                addVolume(volume.name, volume.path, volume.isRemovable)
            }
        }
        return volumes
    }

    if (context != null) {
        // The StorageManager reports every mounted volume, including USB OTG
        // drives on TV boxes whose mount points a manual /storage scan can miss.
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE)
            as? android.os.storage.StorageManager
        for (volume in storageManager?.storageVolumes.orEmpty()) {
            if (volume.state != android.os.Environment.MEDIA_MOUNTED) continue
            val dir: File? = if (android.os.Build.VERSION.SDK_INT >= 30) {
                volume.directory
            } else {
                // getPathFile() is hidden on older APIs; reflection keeps the
                // OTG volume path available on Android 8.x TV boxes.
                try {
                    @Suppress("DEPRECATION")
                    volume.javaClass.getMethod("getPathFile").invoke(volume) as? File
                } catch (_: Exception) {
                    null
                }
            }
            if (dir == null) continue
            val removable = volume.isRemovable
            addVolume(
                name = when {
                    !removable -> "Внутренняя память"
                    volume.getDescription(context).isNullOrBlank() -> "USB-накопитель"
                    else -> volume.getDescription(context) ?: "USB-накопитель"
                },
                path = dir.absolutePath,
                isRemovable = removable
            )
        }
    }

    // Fallback for the rare case the StorageManager misses something: a
    // manual scan of /storage (used before the StorageManager path existed).
    val internal = File(INTERNAL_STORAGE_PATH)
    if (internal.exists() && internal.isDirectory && internal.canRead()) {
        addVolume("Внутренняя память", internal.absolutePath, isRemovable = false)
    }
    val subDirs = File(STORAGE_ROOT).listFiles()
    if (subDirs != null) {
        for (dir in subDirs.sortedBy { it.name }) {
            val name = dir.name
            if (name == "emulated" || name == "self") continue
            if (!dir.isDirectory || name.startsWith(".")) continue
            if (!dir.canRead()) continue
            addVolume("SD-карта", dir.absolutePath, isRemovable = true)
        }
    }

    return volumes
}

data class FileSystemItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isFile: Boolean = !isDirectory,
    val size: Long = 0
)

fun listDirectoryItems(dirPath: String): List<FileSystemItem> {
    getCachedDirectoryScan(dirPath)?.let { return it.files }

    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
        return emptyList()
    }

    val items = mutableListOf<FileSystemItem>()
    val files = dir.listFiles() ?: return emptyList()
    var videoCount = 0

    for (file in files) {
        try {
            val name = file.name
            if (name.startsWith(".")) continue

            if (file.isDirectory) {
                items.add(
                    FileSystemItem(
                        name = name,
                        path = file.absolutePath,
                        isDirectory = true
                    )
                )
            } else if (isVideoFile(name)) {
                items.add(
                    FileSystemItem(
                        name = name,
                        path = file.absolutePath,
                        isDirectory = false,
                        size = file.length()
                    )
                )
                videoCount++
            }
        } catch (_: Exception) {
            // One unreadable entry must not abort the whole listing.
        }
    }

    val sortedItems = items.sortedWith(
        compareBy<FileSystemItem> { !it.isDirectory }
            .thenBy { it.name.lowercase() }
    )

    cacheDirectoryScan(dirPath, sortedItems, videoCount)
    return sortedItems
}

fun listSubdirectories(dirPath: String): List<String> {
    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()
    return dir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
        ?.map { it.absolutePath }
        ?.sortedBy { it.substringAfterLast('/').lowercase() }
        ?: emptyList()
}

fun isVideoFile(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in VIDEO_EXTENSIONS
}

suspend fun findVideosRecursively(dir: File): List<File> = withContext(Dispatchers.IO) {
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext emptyList()

    val result = mutableListOf<File>()
    val stack = ArrayDeque<File>()
    stack.add(dir)
    // Symlink or bind-mount cycles would otherwise spin forever.
    val visited = mutableSetOf(dir.canonicalPath)

    while (stack.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val current = stack.removeLast()
        val files = current.listFiles() ?: continue
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                if (visited.add(file.canonicalPath)) {
                    stack.add(file)
                }
            } else if (isVideoFile(file.name)) {
                result.add(file)
            }
        }
    }

    result
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val df = DecimalFormat("#,##0.#")
    return df.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups.coerceAtMost(units.size - 1)]
}
