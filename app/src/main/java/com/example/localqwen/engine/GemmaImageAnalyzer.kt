package com.example.localqwen.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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

class GemmaImageAnalyzer(private val context: Context) {

    private var engine: LiteRtLmInferenceEngine? = null
    
    var currentStage: String = "idle"
        private set
    
    var lastTempImagePath: String? = null
        private set

    suspend fun loadModel(modelPath: String) {
        currentStage = "loadModel"
        Log.d(TAG, "Starting model load from: $modelPath")
        withContext(Dispatchers.IO) {
            try {
                engine = LiteRtLmInferenceEngine()
                engine?.load(modelPath, context.cacheDir.absolutePath, "gpu")
                Log.d(TAG, "Model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Model load failed", e)
                throw e
            }
        }
    }

    fun unload() {
        Log.d(TAG, "Unloading model...")
        try {
            engine?.let {
                kotlinx.coroutines.runBlocking { it.unload() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during model unload", e)
        } finally {
            engine = null
        }
        Log.d(TAG, "Model unloaded")
    }

    suspend fun analyzeImage(imageUri: Uri, question: String): Flow<String> {
        return withContext(Dispatchers.IO) {
            if (engine == null || engine?.isReady() != true) {
                Log.e(TAG, "Inference attempt while model not ready")
                throw IllegalStateException("Model not loaded")
            }

            // 1. Load and resize image
            currentStage = "imageProcessing"
            Log.d(TAG, "Starting image processing for URI: $imageUri")
            val resizedFile = resizeImageToTempFile(imageUri, 768)
                ?: throw IllegalArgumentException("Could not process image")

            lastTempImagePath = resizedFile.absolutePath
            Log.d(TAG, "Temp image file ready at: ${resizedFile.absolutePath}")

            // 2. Prepare the prompt in Arabic
            val prompt = """
                أنت مساعد ذكي داخل تطبيق نبض.
                مهمتك تحليل الصور والإجابة عنها باللغة العربية بوضوح واختصار.
                حلل محتوى الصورة بدقة.
                إذا سأل المستخدم سؤالًا محددًا عن الصورة، أجب عن السؤال مباشرة.
                إذا كانت الصورة تحتوي على نص، حاول قراءته وشرحه.
                إذا لم تكن متأكدًا من شيء، قل إنك غير متأكد بدل التخمين.
                لا تذكر تفاصيل غير موجودة في الصورة.
                سؤال المستخدم:
                $question
            """.trimIndent()

            currentStage = "inference"
            Log.d(TAG, "Starting vision inference with prompt: $question")

            // 3. Generate vision response
            var firstChunk = true
            val flow = engine!!.generateVision(resizedFile.absolutePath, prompt)

            flow.onStart { 
                Log.d(TAG, "Inference flow started")
                currentStage = "firstChunk"
            }
                .onEach { 
                    if (firstChunk) {
                        Log.d(TAG, "Received first chunk/token from model")
                        firstChunk = false
                        currentStage = "streaming"
                    }
                }
                .onCompletion { cause ->
                    if (cause != null) {
                        Log.e(TAG, "Inference completed with error", cause)
                        currentStage = "error"
                    } else {
                        Log.d(TAG, "Inference completed successfully")
                        currentStage = "completed"
                    }
                    // Always clean up temp file — success or error
                    secureDeleteFile(resizedFile)
                    lastTempImagePath = null
                }
        }
    }

    /**
     * Removes any leftover temporary vision image files from the cache directory.
     * Call this during app startup to clean up after crashes or abnormal terminations.
     */
    fun cleanupTempFiles() {
        try {
            val cacheDir = context.cacheDir
            val tempFiles = cacheDir.listFiles { file ->
                file.name.startsWith(TEMP_FILE_PREFIX) && file.name.endsWith(TEMP_FILE_SUFFIX)
            }
            tempFiles?.forEach { file ->
                secureDeleteFile(file)
                Log.d(TAG, "Cleaned up leftover temp file: ${file.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during temp file cleanup", e)
        }
    }

    private fun resizeImageToTempFile(uri: Uri, maxDimension: Int): File? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            Log.d(TAG, "Original dimensions: ${originalWidth}x${originalHeight}")

            val freshInputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(freshInputStream)
            freshInputStream.close()

            if (originalBitmap == null) return null

            val scale = maxDimension.toFloat() / maxOf(originalWidth, originalHeight)
            val resizedBitmap = if (scale < 1) {
                val targetW = (originalWidth * scale).toInt()
                val targetH = (originalHeight * scale).toInt()
                Log.d(TAG, "Resizing to: ${targetW}x${targetH} (scale: $scale)")
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                Log.d(TAG, "No resizing needed, using original dimensions")
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "${TEMP_FILE_PREFIX}${UUID.randomUUID()}${TEMP_FILE_SUFFIX}")
            val outputStream = FileOutputStream(tempFile)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.close()

            if (resizedBitmap != originalBitmap) {
                resizedBitmap.recycle()
            }
            originalBitmap.recycle()

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

        /**
         * Securely deletes a file by overwriting its contents with zeros before deletion.
         * This prevents recovery of image data from disk after the file is deleted.
         */
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
                // Fallback: attempt simple deletion even if overwrite fails
                try { file.delete() } catch (_: Exception) {}
                Log.w(TAG, "Secure delete partially failed, file deleted without overwrite", e)
            }
        }
    }
}