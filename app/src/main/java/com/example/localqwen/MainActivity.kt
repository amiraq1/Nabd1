package com.example.localqwen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.example.localqwen.chat.Role
import com.example.localqwen.diagnostics.ModelLifecycleObserver
import com.example.localqwen.document.PdfSettings
import com.example.localqwen.model.ModelManager
import com.example.localqwen.ui.compose.NabdApp
import com.example.localqwen.viewmodel.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.localqwen.model.ModelManager.SupportedModel
import com.example.localqwen.model.ModelManager.Companion.SUPPORTED_MODELS
import com.example.localqwen.model.ModelManager.Companion.VISION_MODEL
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var modelPickerLauncher: ActivityResultLauncher<String>
    private lateinit var embeddingModelPickerLauncher: ActivityResultLauncher<String>
    private var pendingImportModel: SupportedModel? = null

    // Held as class properties so handleSettingsAction can reach them
    private var chatViewModel: ChatViewModel? = null
    private var modelViewModel: ModelViewModel? = null
    private var memoryViewModel: MemoryViewModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleSettingsAction(result.data)
            }
        }

        modelPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            // uri handled via ViewModel in handleSettingsAction or similar
        }

        embeddingModelPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            // uri handled via ViewModel
        }
        
        setContent {
            val cv: ChatViewModel = hiltViewModel()
            val mv: ModelViewModel = hiltViewModel()
            val memv: MemoryViewModel = hiltViewModel()

            // Store references so the settings callback can use them
            chatViewModel = cv
            modelViewModel = mv
            memoryViewModel = memv

            // Memory Management: Observe lifecycle to unload/reload model
            DisposableEffect(Unit) {
                val observer = ModelLifecycleObserver(mv)
                lifecycle.addObserver(observer)
                cv.loadActiveSession()
                onDispose { lifecycle.removeObserver(observer) }
            }

            com.example.localqwen.ui.compose.NabdTheme {
                NabdApp(
                    chatViewModel = cv,
                    modelViewModel = mv,
                    memoryViewModel = memv,
                    onOpenSettings = { openSettingsPage(cv, mv, memv) }
                )
            }
        }
    }

    private fun openSettingsPage(chatViewModel: ChatViewModel, modelViewModel: ModelViewModel, memoryViewModel: MemoryViewModel) {
        lifecycleScope.launch {
            val selectedDocumentTitle = chatViewModel.getSelectedDocumentAsync()?.title
            val intent = buildSettingsIntent(chatViewModel, modelViewModel, memoryViewModel, selectedDocumentTitle)
            settingsLauncher.launch(intent)
        }
    }

    private fun buildSettingsIntent(chatViewModel: ChatViewModel, modelViewModel: ModelViewModel, memoryViewModel: MemoryViewModel, selectedDocumentTitle: String?): Intent {
        val appInfo = packageManager.getPackageInfo(packageName, 0)
        val intent = SettingsActivity.createIntent(
            this,
            modelDescription = modelViewModel.selectedModel.value?.displayName ?: "",
            modelStatus = modelViewModel.currentModelStatusLabel(),
            modelGemma3Status = modelViewModel.modelImportStatus(SUPPORTED_MODELS[0]),
            modelE2bStatus = modelViewModel.modelImportStatus(SUPPORTED_MODELS[1]),
            modelE4bStatus = modelViewModel.modelImportStatus(SUPPORTED_MODELS[2]),
            modelVisionStatus = modelViewModel.modelImportStatus(VISION_MODEL),
            documentAnswerLength = chatViewModel.currentDocumentAnswerLength(),
            ragSearchMode = modelViewModel.currentRagMode().name.lowercase(),
            embeddingBackend = modelViewModel.currentEmbeddingBackend().name.lowercase(),
            embeddingModelStatus = modelViewModel.embeddingModelStatus(),
            embeddingIndexCount = modelViewModel.embeddingStore.countIndexes(),
            selectedDocumentTitle = selectedDocumentTitle,
            sessionTitle = "محادثة نشطة",
            appVersion = appInfo.versionName ?: "1.3"
        )
        return intent
    }

    private fun handleSettingsAction(data: Intent?) {
        val action = data?.getStringExtra(SettingsActivity.EXTRA_ACTION) ?: return
        val value = data.getStringExtra(SettingsActivity.EXTRA_VALUE)
        val mv = modelViewModel
        val cv = chatViewModel
        val memv = memoryViewModel

        Log.i(TAG, "Settings action: $action value: $value")

        when (action) {
            // --- Memory ---
            SettingsActivity.ACTION_TOGGLE_MEMORY -> memv?.toggleMemoryEnabled(
                !(memv?.isMemoryEnabled?.value ?: false)
            )
            SettingsActivity.ACTION_CLEAR_MEMORY -> memv?.clearAllMemories()

            // --- RAG / Document ---
            SettingsActivity.ACTION_SET_DOCUMENT_ANSWER_LENGTH -> {
                cv?.setDocumentAnswerLength(value ?: "short")
            }
            SettingsActivity.ACTION_SET_PDF_PAGE_LIMIT -> {
                val limit = value?.toIntOrNull() ?: return
                PdfSettings.setPdfPageLimit(this, limit)
            }
            SettingsActivity.ACTION_SET_RAG_SEARCH_MODE -> {
                mv?.setRagMode(value ?: "auto")
            }
            SettingsActivity.ACTION_CLEAR_SELECTED_DOCUMENT -> cv?.clearSelectedDocument()

            // --- Embedding ---
            SettingsActivity.ACTION_SET_EMBEDDING_BACKEND -> {
                mv?.setEmbeddingBackend(value ?: "auto")
            }
            SettingsActivity.ACTION_DELETE_EMBEDDING_INDEXES -> mv?.deleteEmbeddingIndexes()
            SettingsActivity.ACTION_BUILD_DOCUMENT_SEMANTIC_INDEX -> {
                lifecycleScope.launch {
                    val doc = cv?.getSelectedDocumentAsync()
                    mv?.buildSemanticIndex(doc)
                }
            }

            // --- Chat ---
            SettingsActivity.ACTION_CLEAR_CHAT -> cv?.startNewChat()
            SettingsActivity.ACTION_COPY_CHAT -> {
                val text = cv?.getCurrentMessages()?.joinToString("\n") { "${it.role}: ${it.text}" } ?: return
                copyToClipboard("نبض - المحادثة", text)
            }
            SettingsActivity.ACTION_COPY_LAST_RESPONSE -> {
                copyToClipboard("نبض - آخر رد", cv?.lastAssistantResponse ?: return)
            }

            // --- Model ---
            SettingsActivity.ACTION_LITERT_DIAGNOSTICS -> mv?.runBenchmark()

            // --- Test Report ---
            SettingsActivity.ACTION_TRIGGER_TEST_REPORT -> cv?.triggerTestReport()

            // --- Refresh (fallback for display-only actions) ---
            else -> Log.i(TAG, "Unhandled settings action: $action (display-only, no sync needed)")
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
    }

    private fun showModelManagementDialog(model: SupportedModel, modelViewModel: ModelViewModel) {
        val isImported = modelViewModel.modelManager.isModelImported(model.id)
        val isLoaded = modelViewModel.loadedModelId == model.id && modelViewModel.isEngineReady()

        val options = mutableListOf<String>()
        if (isImported) {
            options.add(if (isLoaded) "إيقاف التشغيل" else "تشغيل الآن")
            options.add("حذف الملف")
        } else {
            options.add("استيراد الملف (.litertlm)")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(model.displayName)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "تشغيل الآن" -> {
                        modelViewModel.selectModel(model)
                        modelViewModel.loadModel()
                    }
                    "إيقاف التشغيل" -> modelViewModel.unloadModel()
                    "حذف الملف" -> {
                        modelViewModel.modelManager.deleteModel(model)
                        modelViewModel.unloadModel()
                        Toast.makeText(this, "تم حذف الملف", Toast.LENGTH_SHORT).show()
                    }
                    "استيراد الملف (.litertlm)" -> {
                        pendingImportModel = model
                        modelPickerLauncher.launch("*/*")
                    }
                }
            }
            .show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
