package com.example.localqwen.engine

import android.content.Context
import android.util.Log
import com.example.localqwen.model.ModelManager
import com.google.ai.edge.litertlm.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Type-Safe Implementation of NabdInferenceEngine using LiteRT-LM Public APIs.
 * NO REFLECTION USED.
 */
@Singleton
class LiteRtLmInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: dagger.Lazy<ModelManager>
) : NabdInferenceEngine {

    private val engineLock = Mutex()
    private val isReleased = AtomicBoolean(false)
    
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    companion object {
        private const val TAG = "LiteRtEngine"
    }

    override suspend fun load(modelPath: String, cacheDir: String) {
        load(modelPath, cacheDir, "cpu")
    }

    override suspend fun load(modelPath: String, cacheDir: String, backendName: String) {
        engineLock.withLock {
            if (isReleased.get()) return
            withContext(Dispatchers.IO) {
                try {
                    unloadInternal()

                    val backendObj = BackendProvider.getBackend(backendName)
                    
                    // Actual Signature from javap and error analysis:
                    // EngineConfig(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages)
                    // Then set cacheDir via reflection if it's missing, or omit it if not supported.
                    // Based on the error "actual type is 'kotlin.String', but 'kotlin.Int?' was expected",
                    // the 6th argument is likely an Int (maybe maxNumImages).
                    // Let's use 0 for the 6th argument.
                    val config = EngineConfig(
                        modelPath,
                        backendObj,
                        backendObj, // Use same for vision
                        backendObj, // Use same for audio
                        2048,       // maxNumTokens
                        null        // maxNumImages (set to null to avoid validation constraints)
                    )
                    
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    
                    engine = newEngine
                    conversation = newEngine.createConversation()
                    
                    Log.i(TAG, "✓ LiteRT Engine initialized on $backendName via Public API")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load engine directly", e)
                    unloadInternal()
                    throw e
                }
            }
        }
    }

    override suspend fun unload() {
        engineLock.withLock { unloadInternal() }
    }

    private suspend fun unloadInternal() {
        withContext(Dispatchers.IO) {
            try {
                conversation?.close()
            } catch (e: Exception) {
            } finally {
                conversation = null
            }

            try {
                engine?.close()
            } catch (e: Exception) {
            } finally {
                engine = null
            }
        }
    }

    override fun isReady(): Boolean = !isReleased.get() && engine != null && conversation != null

    override fun generate(prompt: String): Flow<String> {
        // Safety valve: prevent text generation while vision weights are active
        val mm = modelManager.get()
        val activeId = mm.getActiveModelId()
        if (activeId == GemmaImageAnalyzer.VISION_MODE_TAG) {
            Log.w(TAG, "Desync detected! Vision mode active while generate() called. Forcing rollback.")
            val textModelId = mm.getDefaultTextModelId()
            if (textModelId != null) {
                mm.setActiveModelId(textModelId)
            }
            // Reload the text model
            val textPath = mm.getModelById(textModelId ?: "")
                ?.let { mm.modelPath(it) }
            if (textPath != null) {
                try {
                    runBlocking { load(textPath, context.cacheDir.absolutePath) }
                } catch (e: Exception) {
                    Log.e(TAG, "Rollback load failed", e)
                    return flow { emit("\n⚠️ [خطأ في النظام: فشل استعادة نموذج النص. ${e.localizedMessage ?: "خطأ غير معروف"}]") }
                }
            } else {
                return flow { emit("\n⚠️ [خطأ في النظام: لا يوجد نموذج نص لاستعادته.]") }
            }
        }

        if (isReleased.get()) {
            return flow { emit("\n⚠️ [تم تحرير المحرك. يتعذر بدء التوليد.]") }
        }
        val currentConversation = conversation ?: return flow {
            emit("\n⚠️ [المحرك غير جاهز. جرّب إعادة استيراد النموذج.]")
        }
        val currentEngine = engine ?: return flow {
            emit("\n⚠️ [المحرك غير جاهز. جرّب إعادة استيراد النموذج.]")
        }

        return currentConversation.sendMessageAsync(prompt).map { it.toString() }.catch { e ->
            Log.e(TAG, "Text inference critical failure", e)
            emit("\n⚠️ [خطأ في النظام: تعذر توليد الإجابة. ${e.localizedMessage ?: "انهيار غير معروف في المحرك"}]")
        }
    }

    override fun generateVision(imagePath: String, prompt: String): Flow<String> {
        val mm = modelManager.get()
        val activeId = mm.getActiveModelId()
        if (activeId != GemmaImageAnalyzer.VISION_MODE_TAG) {
            Log.w(TAG, "State mismatch: vision generation requested but model is $activeId. Proceeding anyway.")
        }

        if (isReleased.get()) {
            return flow { emit("\n⚠️ [تم تحرير المحرك. يتعذر تحليل الصورة.]") }
        }
        val currentConversation = conversation ?: return flow {
            emit("\n⚠️ [المحرك غير جاهز لتحليل الصور. جرّب إعادة استيراد النموذج.]")
        }
        val currentEngine = engine ?: return flow {
            emit("\n⚠️ [المحرك غير جاهز لتحليل الصور.]")
        }
        
        val contents = Contents.of(
            Content.ImageFile(imagePath),
            Content.Text(prompt)
        )

        return currentConversation.sendMessageAsync(contents).map { it.toString() }.catch { e ->
            Log.e(TAG, "Vision inference critical failure", e)
            emit("\n⚠️ [خطأ في النظام: تعذر تحليل الصورة. ${e.localizedMessage ?: "انهيار غير معروف في محرك الرؤية"}]")
        }
    }

    override suspend fun resetConversation() = withContext(Dispatchers.IO) {
        engineLock.withLock {
            val currentEngine = engine ?: return@withLock
            try {
                conversation?.close()
            } catch (_: Exception) {}
            
            conversation = currentEngine.createConversation()
        }
    }

    override fun close() {
        if (isReleased.compareAndSet(false, true)) {
            GlobalScope.launch(Dispatchers.IO) { unload() }
        }
    }

    override suspend fun closeSuspend() {
        if (isReleased.compareAndSet(false, true)) {
            unload()
        }
    }
}
