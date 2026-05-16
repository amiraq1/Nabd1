package com.example.localqwen

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.Role
import com.example.localqwen.model.ModelManager
import com.example.localqwen.ui.compose.NabdApp
import com.example.localqwen.viewmodel.ChatViewModel
import com.example.localqwen.viewmodel.MemoryViewModel
import com.example.localqwen.viewmodel.ModelViewModel
import com.example.localqwen.viewmodel.MemoryCommandResult
import com.example.localqwen.viewmodel.ModelState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()
    private val modelViewModel: ModelViewModel by viewModels()
    private val memoryViewModel: MemoryViewModel by viewModels()

    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var modelPickerLauncher: ActivityResultLauncher<String>
    private var pendingImportModel: ModelManager.SupportedModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        chatViewModel.loadActiveSession()

        settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleSettingsAction(result.data)
            }
        }

        modelPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                pendingImportModel?.let { model ->
                    modelViewModel.importModel(model, it)
                }
            }
        }
        
        setContent {
            NabdApp(
                chatViewModel = chatViewModel,
                modelViewModel = modelViewModel,
                memoryViewModel = memoryViewModel,
                onOpenSettings = { openSettingsPage() }
            )
        }
    }

    private fun openSettingsPage() {
        val intent = SettingsActivity.createIntent(
            this,
            modelDescription = modelViewModel.selectedModel.value?.displayName ?: "",
            modelStatus = modelViewModel.currentModelStatusLabel(),
            modelE2bStatus = modelViewModel.modelImportStatus(ModelManager.SUPPORTED_MODELS[0]),
            modelE4bStatus = modelViewModel.modelImportStatus(ModelManager.SUPPORTED_MODELS[1]),
            documentAnswerLength = chatViewModel.currentDocumentAnswerLength(),
            ragSearchMode = modelViewModel.currentRagMode().name.lowercase(),
            embeddingBackend = modelViewModel.currentEmbeddingBackend().name.lowercase(),
            embeddingModelStatus = modelViewModel.embeddingModelStatus(),
            embeddingIndexCount = 0, // Placeholder
            selectedDocumentTitle = chatViewModel.documentStore.getSelectedDocumentId(), // Should be title
            sessionTitle = "محادثة نشطة",
            appVersion = "1.4"
        )
        settingsLauncher.launch(intent)
    }

    private fun handleSettingsAction(data: Intent?) {
        val action = data?.getStringExtra(SettingsActivity.EXTRA_ACTION) ?: return
        val value = data.getStringExtra(SettingsActivity.EXTRA_VALUE)

        when (action) {
            SettingsActivity.ACTION_MANAGE_MODEL_E2B -> {
                val model = ModelManager.SUPPORTED_MODELS[0]
                showModelManagementDialog(model)
            }
            SettingsActivity.ACTION_MANAGE_MODEL_E4B -> {
                val model = ModelManager.SUPPORTED_MODELS[1]
                showModelManagementDialog(model)
            }
            SettingsActivity.ACTION_CLEAR_CHAT -> chatViewModel.startNewChat()
            SettingsActivity.ACTION_TOGGLE_MEMORY -> {
                val enabled = !memoryViewModel.isMemoryEnabled.value!!
                memoryViewModel.toggleMemoryEnabled(enabled)
                Toast.makeText(this, if (enabled) "تم تفعيل الذاكرة" else "تم تعطيل الذاكرة", Toast.LENGTH_SHORT).show()
            }
            SettingsActivity.ACTION_CLEAR_MEMORY -> memoryViewModel.clearAllMemories()
            SettingsActivity.ACTION_HELP -> {
                // Show help dialog or activity
            }
            // Add other actions as needed
        }
    }

    private fun showModelManagementDialog(model: ModelManager.SupportedModel) {
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
