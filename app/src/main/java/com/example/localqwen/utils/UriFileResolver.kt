package com.example.localqwen.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object UriFileResolver {
    private const val TAG = "UriFileResolver"

    suspend fun copyUriToCache(context: Context, uri: Uri, fileNamePrefix: String = "temp_"): String {
        return withContext(Dispatchers.IO) {
            try {
                // If it's already a file URI, we can just return its path if it exists
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        return@withContext file.absolutePath
                    }
                }

                val cacheDir = File(context.cacheDir, "uri_cache").apply { mkdirs() }
                val tempFile = File(cacheDir, "${fileNamePrefix}${UUID.randomUUID()}")

                Log.d(TAG, "ORIGINAL_URI=${uri}")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("Could not open input stream for URI: $uri")

                Log.d(TAG, "RESOLVED_PATH=${tempFile.absolutePath}")
                tempFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy uri", e)
                throw e
            }
        }
    }
}
