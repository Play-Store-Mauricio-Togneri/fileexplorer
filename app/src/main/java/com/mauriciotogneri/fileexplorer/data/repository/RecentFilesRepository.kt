package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecentFilesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun getRecentFiles(): List<RecentFile> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_RECENT_FILES, null) ?: return@withContext emptyList()
        try {
            val array = JSONArray(json)
            val files = mutableListOf<RecentFile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.getString(JSON_PATH)
                // Only include files that still exist
                if (File(path).exists()) {
                    files.add(
                        RecentFile(
                            path = path,
                            name = obj.getString(JSON_NAME),
                            mimeType = obj.getString(JSON_MIME_TYPE),
                            lastOpenedTimestamp = obj.getLong(JSON_TIMESTAMP)
                        )
                    )
                }
            }
            files.sortedByDescending { it.lastOpenedTimestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRecentFile(file: File) = withContext(Dispatchers.IO) {
        if (file.isDirectory) return@withContext

        val currentFiles = getRecentFiles().toMutableList()

        // Remove existing entry for same path
        currentFiles.removeAll { it.path == file.absolutePath }

        // Add new entry at the beginning
        val newEntry = RecentFile(
            path = file.absolutePath,
            name = file.name,
            mimeType = MimeTypeUtil.getMimeType(file),
            lastOpenedTimestamp = System.currentTimeMillis()
        )
        currentFiles.add(0, newEntry)

        // Keep only the most recent MAX_RECENT_FILES
        val trimmedList = currentFiles.take(MAX_RECENT_FILES)

        // Save to SharedPreferences
        saveRecentFiles(trimmedList)
    }

    private fun saveRecentFiles(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file ->
            val obj = JSONObject().apply {
                put(JSON_PATH, file.path)
                put(JSON_NAME, file.name)
                put(JSON_MIME_TYPE, file.mimeType)
                put(JSON_TIMESTAMP, file.lastOpenedTimestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RECENT_FILES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "recent_files"
        private const val KEY_RECENT_FILES = "files"
        private const val MAX_RECENT_FILES = 20

        private const val JSON_PATH = "path"
        private const val JSON_NAME = "name"
        private const val JSON_MIME_TYPE = "mimeType"
        private const val JSON_TIMESTAMP = "timestamp"
    }
}
