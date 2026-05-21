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
import java.util.UUID

class GemmaImageAnalyzer(private val context: Context) {

    private var engine: LiteRtLmInferenceEngine? = null

    suspend fun loadModel(modelPath: String) {
        Log.d("GemmaImageAnalyzer", "Starting model load from: $modelPath")
        withContext(Dispatchers.IO) {
            try {
                engine = LiteRtLmInferenceEngine()
                engine?.load(modelPath, context.cacheDir.absolutePath, "cpu")
                Log.d("GemmaImageAnalyzer", "Model loaded successfully")
            } catch (e: Exception) {
                Log.e("GemmaImageAnalyzer", "Model load failed", e)
                throw e
            }
        }
    }

    fun unload() {
        Log.d("GemmaImageAnalyzer", "Unloading model...")
        engine?.let {
            kotlinx.coroutines.runBlocking { it.unload() }
        }
        engine = null
        Log.d("GemmaImageAnalyzer", "Model unloaded")
    }

    suspend fun analyzeImage(imageUri: Uri, question: String): Flow<String> {
        return withContext(Dispatchers.IO) {
            if (engine == null || engine?.isReady() != true) {
                Log.e("GemmaImageAnalyzer", "Inference attempt while model not ready")
                throw IllegalStateException("Model not loaded")
            }

            // 1. Load and resize image
            Log.d("GemmaImageAnalyzer", "Starting image processing for URI: $imageUri")
            val resizedFile = resizeImageToTempFile(imageUri, 768)
                ?: throw IllegalArgumentException("Could not process image")

            Log.d("GemmaImageAnalyzer", "Temp image file ready at: ${resizedFile.absolutePath}")

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

            Log.d("GemmaImageAnalyzer", "Starting vision inference with prompt: $question")

            // 3. Generate vision response
            var firstChunk = true
            val flow = engine!!.generateVision(resizedFile.absolutePath, prompt)

            flow.onStart { Log.d("GemmaImageAnalyzer", "Inference flow started") }
                .onEach { 
                    if (firstChunk) {
                        Log.d("GemmaImageAnalyzer", "Received first chunk/token from model")
                        firstChunk = false
                    }
                }
                .onCompletion { 
                    Log.d("GemmaImageAnalyzer", "Inference completed successfully")
                    // Optional: delete temp file
                    if (resizedFile.exists()) resizedFile.delete()
                }
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
            Log.d("GemmaImageAnalyzer", "Original dimensions: ${originalWidth}x${originalHeight}")

            val freshInputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(freshInputStream)
            freshInputStream.close()

            if (originalBitmap == null) return null

            val scale = maxDimension.toFloat() / maxOf(originalWidth, originalHeight)
            val resizedBitmap = if (scale < 1) {
                val targetW = (originalWidth * scale).toInt()
                val targetH = (originalHeight * scale).toInt()
                Log.d("GemmaImageAnalyzer", "Resizing to: ${targetW}x${targetH} (scale: $scale)")
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                Log.d("GemmaImageAnalyzer", "No resizing needed, using original dimensions")
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "vision_temp_${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.close()

            if (resizedBitmap != originalBitmap) {
                resizedBitmap.recycle()
            }
            originalBitmap.recycle()

            return tempFile
        } catch (e: Exception) {
            Log.e("GemmaImageAnalyzer", "Error processing image resizing", e)
            return null
        }
    }
}