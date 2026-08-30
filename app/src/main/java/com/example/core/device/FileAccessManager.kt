package com.example.core.device

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import java.io.File

data class StorageReport(
    val internalTotalGb: Double,
    val internalAvailableGb: Double,
    val internalUsedGb: Double,
    val appDataBytes: Long,
    val appCacheBytes: Long,
    val mediaCounts: Map<String, Int>,
    val isExternalMounted: Boolean
)

class FileAccessManager(private val context: Context) {
    private val TAG = "JARVIS_FileManager"

    fun getStorageReport(): StorageReport {
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val blockSize = stat.blockSizeLong
        val totalBytes = stat.blockCountLong * blockSize
        val availBytes = stat.availableBlocksLong * blockSize
        val usedBytes = totalBytes - availBytes

        val totalGb = totalBytes.toDouble() / (1024 * 1024 * 1024)
        val availGb = availBytes.toDouble() / (1024 * 1024 * 1024)
        val usedGb = usedBytes.toDouble() / (1024 * 1024 * 1024)

        val appDataSize = getDirectorySize(context.filesDir)
        val appCacheSize = getDirectorySize(context.cacheDir)
        val mediaCounts = queryMediaCounts()

        return StorageReport(
            internalTotalGb = totalGb,
            internalAvailableGb = availGb,
            internalUsedGb = usedGb,
            appDataBytes = appDataSize,
            appCacheBytes = appCacheSize,
            mediaCounts = mediaCounts,
            isExternalMounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        )
    }

    fun clearAppCache(): Long {
        val cacheSize = getDirectorySize(context.cacheDir)
        deleteDirectoryContents(context.cacheDir)
        deleteDirectoryContents(context.codeCacheDir)
        return cacheSize
    }

    fun createOpenFileIntent(mimeType: String = "*/*"): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createSaveFileIntent(fileName: String, mimeType: String = "text/plain"): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun queryMediaCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val resolver: ContentResolver = context.contentResolver

        fun countUri(uri: Uri, label: String) {
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                counts[label] = cursor?.count ?: 0
            } catch (e: Exception) {
                counts[label] = 0
            } finally {
                cursor?.close()
            }
        }

        try {
            countUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Images")
            countUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "Audio")
            countUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Video")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                countUri(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Downloads")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Media query restricted or permission pending", e)
        }
        return counts
    }

    private fun getDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getDirectorySize(f) else f.length()
        }
        return size
    }

    private fun deleteDirectoryContents(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        val files = dir.listFiles() ?: return true
        var allOk = true
        for (f in files) {
            if (f.isDirectory) {
                deleteDirectoryContents(f)
            }
            if (!f.delete()) {
                allOk = false
            }
        }
        return allOk
    }
}
