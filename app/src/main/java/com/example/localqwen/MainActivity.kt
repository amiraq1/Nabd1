package com.example.localqwen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
            val chatViewModel: ChatViewModel = hiltViewModel()
            val modelViewModel: ModelViewModel = hiltViewModel()
            val memoryViewModel: MemoryViewModel = hiltViewModel()

            // Memory Management: Observe lifecycle to unload/reload model
            LaunchedEffect(Unit) {
                lifecycle.addObserver(ModelLifecycleObserver(modelViewModel))
                chatViewModel.loadActiveSession()
            }

            com.example.localqwen.ui.compose.NabdTheme {
                NabdApp(
                    chatViewModel = chatViewModel,
                    modelViewModel = modelViewModel,
                    memoryViewModel = memoryViewModel,
                    onOpenSettings = { openSettingsPage(chatViewModel, modelViewModel, memoryViewModel) }
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
        // Since ViewModels are now inside setContent, we might need a different way to handle this
        // Or just trigger a refresh event
        Toast.makeText(this, "حدثت الإعدادات", Toast.LENGTH_SHORT).show()
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
}
