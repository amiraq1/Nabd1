package com.example.localqwen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.localqwen.chat.Role
import androidx.lifecycle.lifecycleScope
import com.example.localqwen.document.PdfSettings
import com.example.localqwen.model.ModelManager
import com.example.localqwen.ui.compose.NabdApp
import com.example.localqwen.viewmodel.ChatViewModel
import com.example.localqwen.viewmodel.MemoryViewModel
import com.example.localqwen.viewmodel.ModelViewModel
import com.example.localqwen.viewmodel.MemoryCommandResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var embeddingModelPickerLauncher: ActivityResultLauncher<String>
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
                pendingImportModel = null
            }
        }

        embeddingModelPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { modelViewModel.importEmbeddingModel(it) }
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
        lifecycleScope.launch {
            val selectedDocumentTitle = chatViewModel.getSelectedDocumentAsync()?.title
            val intent = buildSettingsIntent(selectedDocumentTitle)
            settingsLauncher.launch(intent)
        }
    }

    private fun buildSettingsIntent(selectedDocumentTitle: String?): Intent {
        val appInfo = packageManager.getPackageInfo(packageName, 0)
        val intent = SettingsActivity.createIntent(
            this,
            modelDescription = modelViewModel.selectedModel.value?.displayName ?: "",
            modelStatus = modelViewModel.currentModelStatusLabel(),
            modelGemma3Status = modelViewModel.modelImportStatus(ModelManager.SUPPORTED_MODELS[0]),
            modelE2bStatus = modelViewModel.modelImportStatus(ModelManager.SUPPORTED_MODELS[1]),
            modelE4bStatus = modelViewModel.modelImportStatus(ModelManager.SUPPORTED_MODELS[2]),
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

        when (action) {
            SettingsActivity.ACTION_MANAGE_MODEL_GEMMA3 -> {
                val model = ModelManager.SUPPORTED_MODELS[0]
                showModelManagementDialog(model)
            }
            SettingsActivity.ACTION_MANAGE_MODEL_E2B -> {
                val model = ModelManager.SUPPORTED_MODELS[1]
                showModelManagementDialog(model)
            }
            SettingsActivity.ACTION_MANAGE_MODEL_E4B -> {
                val model = ModelManager.SUPPORTED_MODELS[2]
                showModelManagementDialog(model)
            }
            SettingsActivity.ACTION_IMPORT_VISION_MODEL -> showModelManagementDialog(ModelManager.VISION_MODEL)
            SettingsActivity.ACTION_DELETE_VISION_MODEL -> {
                modelViewModel.modelManager.deleteModel(ModelManager.VISION_MODEL)
                Toast.makeText(this, "تم حذف نموذج الرؤية", Toast.LENGTH_SHORT).show()
            }
            SettingsActivity.ACTION_IMPORT_EMBEDDING_MODEL -> embeddingModelPickerLauncher.launch("*/*")
            SettingsActivity.ACTION_DELETE_EMBEDDING_MODEL -> modelViewModel.deleteEmbeddingModel()
            SettingsActivity.ACTION_DELETE_EMBEDDING_INDEXES -> modelViewModel.deleteEmbeddingIndexes()
            SettingsActivity.ACTION_BUILD_DOCUMENT_SEMANTIC_INDEX -> {
                lifecycleScope.launch {
                    modelViewModel.buildSemanticIndex(chatViewModel.getSelectedDocumentAsync())
                }
            }
            SettingsActivity.ACTION_SET_DOCUMENT_ANSWER_LENGTH -> {
                chatViewModel.setDocumentAnswerLength(value ?: "short")
                Toast.makeText(this, "تم تحديث طول إجابة المستند", Toast.LENGTH_SHORT).show()
            }
            SettingsActivity.ACTION_SET_RAG_SEARCH_MODE -> modelViewModel.setRagMode(value ?: "auto")
            SettingsActivity.ACTION_SET_PDF_PAGE_LIMIT -> {
                PdfSettings.setPdfPageLimit(this, value?.toIntOrNull() ?: 10)
                Toast.makeText(this, "تم تحديث حد صفحات PDF", Toast.LENGTH_SHORT).show()
            }
            SettingsActivity.ACTION_SET_EMBEDDING_BACKEND -> modelViewModel.setEmbeddingBackend(value ?: "auto")
            SettingsActivity.ACTION_CLEAR_SELECTED_DOCUMENT -> chatViewModel.clearSelectedDocument()
            SettingsActivity.ACTION_COPY_CHAT -> copyChatToClipboard()
            SettingsActivity.ACTION_COPY_LAST_RESPONSE -> copyLastResponseToClipboard()
            SettingsActivity.ACTION_CLEAR_CHAT -> chatViewModel.startNewChat()
            SettingsActivity.ACTION_TOGGLE_MEMORY -> {
                val enabled = !memoryViewModel.isMemoryEnabled.value!!
                memoryViewModel.toggleMemoryEnabled(enabled)
                Toast.makeText(this, if (enabled) "تم تفعيل الذاكرة" else "تم تعطيل الذاكرة", Toast.LENGTH_SHORT).show()
            }
            SettingsActivity.ACTION_CLEAR_MEMORY -> memoryViewModel.clearAllMemories()
            SettingsActivity.ACTION_SHOW_MEMORY -> {
                val result = memoryViewModel.handleMemoryCommand("ماذا تتذكر عني؟")
                if (result is MemoryCommandResult.ShowList) chatViewModel.addSystemMessage(result.text)
            }
            SettingsActivity.ACTION_HELP -> {
                showInfoDialog("مساعدة نبض", "استورد نموذجًا، ثم ابدأ المحادثة. أضف مستندًا من زر الإرفاق واسأل عنه مباشرة.")
            }
            SettingsActivity.ACTION_MODEL_IMPORT_HELP -> showInfoDialog(
                "دليل استيراد النماذج",
                "اختر ملف .litertlm للنموذج المناسب. ابدأ بـ Gemma E2B للأجهزة المتوسطة، واستخدم E4B للأجهزة الأقوى."
            )
            SettingsActivity.ACTION_READINESS_CHECK -> showReadinessDialog()
            SettingsActivity.ACTION_LITERT_DIAGNOSTICS -> showInfoDialog("تشخيص LiteRT", liteRtDiagnosticsText())
            SettingsActivity.ACTION_RAG_DIAGNOSTICS -> showInfoDialog("تشخيص RAG", ragDiagnosticsText())
            SettingsActivity.ACTION_COPY_BETA_REPORT -> copyBetaReportToClipboard()
            SettingsActivity.ACTION_WHATS_NEW -> showInfoDialog("ما الجديد", "تحسينات على البناء، الذاكرة، المستندات، إعدادات RAG، واستقرار استيراد النماذج.")
            SettingsActivity.ACTION_ABOUT -> showInfoDialog("حول نبض", "نبض مساعد ذكاء اصطناعي عربي محلي يعمل على الجهاز ويحافظ على خصوصية المستخدم.")
            SettingsActivity.ACTION_PRIVACY_POLICY -> openUrl("https://github.com/amiraq1/Nabd1/blob/main/PRIVACY_POLICY.md")
            SettingsActivity.ACTION_SEND_FEEDBACK -> openFeedback()
            SettingsActivity.ACTION_OPEN_DOCUMENT_LIBRARY -> showInfoDialog("المستندات", "مكتبة المستندات محفوظة محليًا. يمكن تطوير شاشة إدارة كاملة للمستندات في المرحلة التالية.")
            SettingsActivity.ACTION_OPEN_CHAT_HISTORY -> showInfoDialog("سجل المحادثات", "سجل المحادثات محفوظ محليًا. يمكن تطوير شاشة تنقل بين الجلسات كتحسين لاحق.")
            SettingsActivity.ACTION_LOCAL_MODEL_MANAGER -> showReadinessDialog()
            SettingsActivity.ACTION_BACKGROUND_TASKS -> showInfoDialog("مهام الخلفية", "تعمل معالجة PDF عبر WorkManager عند إرفاق ملف PDF.")
            SettingsActivity.ACTION_TRIGGER_TEST_REPORT -> {
                chatViewModel.triggerTestReport()
                Toast.makeText(this, "جاري إنشاء تقرير اختبار...", Toast.LENGTH_SHORT).show()
            }
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

    private fun copyChatToClipboard() {
        val text = chatViewModel.getCurrentMessages().joinToString("\n\n") { message ->
            val label = when (message.role) {
                Role.USER -> "أنت"
                Role.ASSISTANT -> "نبض"
                Role.SYSTEM -> "النظام"
            }
            "$label:\n${message.text}"
        }
        copyToClipboard("محادثة نبض", text)
    }

    private fun copyLastResponseToClipboard() {
        copyToClipboard("آخر رد من نبض", chatViewModel.lastAssistantResponse)
    }

    private fun copyBetaReportToClipboard() {
        val report = buildString {
            appendLine("تقرير بيتا - نبض")
            appendLine("النموذج: ${modelViewModel.selectedModel.value?.displayName.orEmpty()}")
            appendLine("حالة النموذج: ${modelViewModel.currentModelStatusLabel()}")
            appendLine("نموذج التضمين: ${modelViewModel.embeddingModelStatus()}")
            appendLine("وضع RAG: ${modelViewModel.currentRagMode().name}")
            appendLine("محرك التضمين: ${modelViewModel.currentEmbeddingBackend().name}")
            appendLine("عدد الفهارس: ${modelViewModel.embeddingStore.countIndexes()}")
            appendLine("آخر زمن أول رد: ${chatViewModel.lastFirstTokenLatencyMs ?: "غير متاح"}")
            appendLine("آخر مدة توليد: ${chatViewModel.lastGenerationDurationMs ?: "غير متاح"}")
            appendLine("لا يتضمن هذا التقرير نصوص المحادثات أو محتوى المستندات.")
        }
        copyToClipboard("تقرير بيتا نبض", report)
    }

    private fun copyToClipboard(label: String, text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "لا يوجد محتوى للنسخ", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
    }

    private fun showReadinessDialog() {
        showInfoDialog(
            "فحص الجاهزية",
            listOf(
                "النموذج الحالي: ${modelViewModel.selectedModel.value?.displayName.orEmpty()}",
                "حالة النموذج: ${modelViewModel.currentModelStatusLabel()}",
                "Gemma E2B: ${modelViewModel.modelImportStatus(ModelManager.SUPPORTED_MODELS[0])}",
                "Gemma E4B: ${modelViewModel.modelImportStatus(ModelManager.SUPPORTED_MODELS[1])}",
                "نموذج التضمين: ${modelViewModel.embeddingModelStatus()}",
                "الفهارس الدلالية: ${modelViewModel.embeddingStore.countIndexes()}"
            ).joinToString("\n")
        )
    }

    private fun liteRtDiagnosticsText(): String {
        return listOf(
            "المحرك: LiteRT-LM",
            "جاهز: ${if (modelViewModel.isEngineReady()) "نعم" else "لا"}",
            "النموذج المحمل: ${modelViewModel.loadedModelId ?: "لا يوجد"}",
            "آخر خطأ: ${modelViewModel.localEngineLastErrorMessage ?: "لا يوجد"}"
        ).joinToString("\n")
    }

    private fun ragDiagnosticsText(): String {
        return listOf(
            "وضع البحث: ${modelViewModel.currentRagMode().name}",
            "محرك التضمين: ${modelViewModel.currentEmbeddingBackend().name}",
            "نموذج التضمين: ${modelViewModel.embeddingModelStatus()}",
            "عدد الفهارس: ${modelViewModel.embeddingStore.countIndexes()}",
            "المستند المحدد: ${chatViewModel.documentStore.getSelectedDocumentId() ?: "لا يوجد"}"
        ).joinToString("\n")
    }

    private fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun openFeedback() {
        openUrl("https://github.com/amiraq1/Nabd1/issues")
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }
            .onFailure {
                copyToClipboard("رابط نبض", url)
            }
    }
}
