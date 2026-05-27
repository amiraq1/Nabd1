package com.example.localqwen.diagnostics

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object NabdDiagnosticLogger {

    private const val ERROR_LOG_FILENAME = "nabd_gemma_error_log.txt"

    data class GemmaErrorContext(
        val stage: String,
        val modelPath: String,
        val tempImagePath: String? = null,
        val promptLength: Int? = null,
        val exception: Throwable
    )

    fun writeGemmaErrorReport(context: Context, errorCtx: GemmaErrorContext, isTest: Boolean = false): File? {
        val fileName = if (isTest) "nabd_gemma_error_log_test.txt" else ERROR_LOG_FILENAME
        return try {
            val report = StringBuilder()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            report.appendLine("=== Nabd Gemma 3 Diagnostic Report ===")
            if (isTest) report.appendLine("!!! THIS IS A TEST REPORT !!!")
            report.appendLine("Time: ${sdf.format(Date())}")
            report.appendLine()
            
            report.appendLine("--- Device Info ---")
            report.appendLine("Manufacturer: ${Build.MANUFACTURER}")
            report.appendLine("Model: ${Build.MODEL}")
            report.appendLine("SDK: ${Build.VERSION.SDK_INT}")
            report.appendLine("Package: ${context.packageName}")
            val pInfo = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
            report.appendLine("Version: ${pInfo?.versionName ?: "unknown"}")
            report.appendLine()
            
            report.appendLine("--- Model Info ---")
            report.appendLine("Model ID: gemma_3n_e2b_it")
            val modelFile = File(errorCtx.modelPath)
            report.appendLine("Path: ${errorCtx.modelPath}")
            report.appendLine("Exists: ${modelFile.exists()}")
            report.appendLine("Size: ${modelFile.length()} bytes")
            report.appendLine()
            
            report.appendLine("--- Vision Info ---")
            report.appendLine("Stage: ${errorCtx.stage}")
            errorCtx.tempImagePath?.let {
                val imgFile = File(it)
                report.appendLine("Temp Image Path: $it")
                report.appendLine("Temp Image Exists: ${imgFile.exists()}")
                report.appendLine("Temp Image Size: ${imgFile.length()} bytes")
            }
            report.appendLine("Prompt Length: ${errorCtx.promptLength ?: "N/A"}")
            report.appendLine()
            
            report.appendLine("--- Error Details ---")
            report.appendLine("Message: ${errorCtx.exception.message}")
            report.appendLine("Stacktrace:")
            report.appendLine(errorCtx.exception.stackTraceToString())
            report.appendLine()
            report.appendLine("=== End of Report ===")

            val reportText = report.toString()
            var logFile: File? = null

            // 1. Try saving to user-accessible Downloads via MediaStore (Android 10+)
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        output.write(reportText.toByteArray())
                    }
                    Log.d("NabdDiagnosticLogger", "Report saved to Downloads via MediaStore: $uri")
                }
            } catch (e: Exception) {
                Log.e("NabdDiagnosticLogger", "MediaStore save failed", e)
            }

            // 2. Save a copy to External Files Dir (accessible via Android/data/.../Documents)
            val extDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDocsDir != null) {
                logFile = File(extDocsDir, fileName)
                logFile.writeText(reportText)
                Log.e("NabdDiagnosticLogger", "Writing Gemma error report...")
                Log.e("NabdDiagnosticLogger", "Report saved to: ${logFile.absolutePath}")
                Log.e("NabdDiagnosticLogger", "Report exists: ${logFile.exists()}, size=${logFile.length()}")
            }

            // 3. Fallback to internal cache if external fails
            if (logFile == null || !logFile.exists()) {
                logFile = File(context.cacheDir, fileName)
                logFile.writeText(reportText)
            }

            logFile
        } catch (e: Exception) {
            Log.e("NabdDiagnosticLogger", "Failed to create diagnostic report", e)
            null
        }
    }

    fun getLogFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}