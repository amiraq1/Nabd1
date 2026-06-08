package com.example.localqwen.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaImageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: NabdInferenceEngine
) : AutoCloseable {

    var currentStage: String = "idle"
        private set
    
    var lastTempImagePath: String? = null
        private set

    suspend fun loadModel(modelPath: String) {
        currentStage = "loadModel"
        Log.d(TAG, "Starting model load from: $modelPath")
        withContext(Dispatchers.IO) {
            try {
                engine.load(modelPath, context.cacheDir.absolutePath, "gpu")
                Log.d(TAG, "Model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Model load failed", e)
                throw e
            }
        }
    }

    override fun close() {
        Log.d(TAG, "Unloading image analyzer resources...")
        // Since engine is a singleton, we don't necessarily want to close it here
        // unless we are sure it's not used elsewhere.
        // For now, we follow the previous pattern.
        // engine.close() 
    }

    fun unload() = close()

    suspend fun analyzeImage(imageUri: Uri, question: String): Flow<String> {
        return withContext(Dispatchers.IO) {
            if (!engine.isReady()) {
                Log.e(TAG, "Inference attempt while model not ready")
                throw IllegalStateException("Model not loaded")
            }

            // 1. Load and resize image
            currentStage = "imageProcessing"
            val resizedFile = resizeImageToTempFile(imageUri, 768)
                ?: throw IllegalArgumentException("Could not process image")

            lastTempImagePath = resizedFile.absolutePath

            // 2. Prepare the prompt
            val prompt = """
                أنت مساعد ذكي داخل تطبيق نبض.
                مهمتك تحليل الصور والإجابة عنها باللغة العربية بوضوح واختصار.
                حلل محتوى الصورة بدقة.
                سؤال المستخدم:
                $question
            """.trimIndent()

            currentStage = "inference"

            // 3. Generate vision response
            var firstChunk = true
            val flow = engine.generateVision(resizedFile.absolutePath, prompt)

            flow.onStart { currentStage = "firstChunk" }
                .onEach { 
                    if (firstChunk) {
                        firstChunk = false
                        currentStage = "streaming"
                    }
                }
                .onCompletion { cause ->
                    if (cause != null) {
                        currentStage = "error"
                    } else {
                        currentStage = "completed"
                    }
                    secureDeleteFile(resizedFile)
                    lastTempImagePath = null
                }
        }
    }

    private suspend fun resizeImageToTempFile(uri: Uri, maxDimension: Int): File? {
        try {
            val localPath = com.example.localqwen.utils.UriFileResolver.copyUriToCache(context, uri, "vision_raw_")
            val localFile = File(localPath)

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(localPath, options)

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            val originalBitmap = BitmapFactory.decodeFile(localPath)
            if (originalBitmap == null) {
                localFile.delete()
                return null
            }

            val scale = maxDimension.toFloat() / maxOf(originalWidth, originalHeight)
            val resizedBitmap = if (scale < 1) {
                val targetW = (originalWidth * scale).toInt()
                val targetH = (originalHeight * scale).toInt()
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "${TEMP_FILE_PREFIX}${UUID.randomUUID()}${TEMP_FILE_SUFFIX}")
            val outputStream = FileOutputStream(tempFile)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.close()

            if (resizedBitmap != originalBitmap) resizedBitmap.recycle()
            originalBitmap.recycle()
            
            localFile.delete()

            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image resizing", e)
            return null
        }
    }

    companion object {
        private const val TAG = "GemmaImageAnalyzer"
        private const val TEMP_FILE_PREFIX = "vision_temp_"
        private const val TEMP_FILE_SUFFIX = ".jpg"

        private fun secureDeleteFile(file: File) {
            try {
                if (!file.exists()) return
                val length = file.length()
                if (length > 0) {
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.seek(0)
                        val zeros = ByteArray(minOf(length, 8192L).toInt())
                        var remaining = length
                        while (remaining > 0) {
                            val toWrite = minOf(remaining, zeros.size.toLong()).toInt()
                            raf.write(zeros, 0, toWrite)
                            remaining -= toWrite
                        }
                        raf.fd.sync()
                    }
                }
                file.delete()
            } catch (e: Exception) {
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }
}
