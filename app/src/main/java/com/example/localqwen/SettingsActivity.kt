package com.example.localqwen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.localqwen.document.PdfSettings
import com.example.localqwen.memory.MemoryStore
import com.example.localqwen.ui.compose.NabdSettingsScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.activity.result.contract.ActivityResultContracts
import com.example.localqwen.viewmodel.ModelSetupState
import com.example.localqwen.ui.compose.ModelSetupWizardSheet
import com.example.localqwen.ui.dev.ModelRuntimeDevScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

 codex/improve-chat-usability
    private lateinit var mainContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvModel: TextView
    private lateinit var tvDoc: TextView
    private lateinit var tvIndex: TextView
    private lateinit var tvVersion: TextView

class SettingsActivity : AppCompatActivity() {
 main

    private var currentModelDescription: String = ""
    private var currentModelStatus: String = ""
    private var currentModelGemma3Status: String = ""
    private var currentModelE2bStatus: String = ""
    private var currentModelE4bStatus: String = ""
    private var currentModelVisionStatus: String = ""
    private var currentDocumentAnswerLength: String = "short"
    private var currentRagSearchMode: String = "auto"
    private var currentEmbeddingBackend: String = "auto"
    private var currentPdfPageLimit: Int = 10
    private var embeddingModelStatus: String = ""
    private var embeddingIndexCount: Int = 0
    private var selectedDocumentId: String? = null
    private var selectedDocumentTitle: String? = null
    private var currentSessionTitle: String = ""
    private var appVersion: String = ""

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val modelViewModel = androidx.lifecycle.ViewModelProvider(this)[com.example.localqwen.viewmodel.ModelViewModel::class.java]

        currentModelDescription = intent.getStringExtra(EXTRA_MODEL_DESCRIPTION).orEmpty()
        currentModelStatus = intent.getStringExtra(EXTRA_MODEL_STATUS).orEmpty()
        currentModelGemma3Status = intent.getStringExtra(EXTRA_MODEL_GEMMA3_STATUS).orEmpty()
        currentModelE2bStatus = intent.getStringExtra(EXTRA_MODEL_E2B_STATUS).orEmpty()
        currentModelE4bStatus = intent.getStringExtra(EXTRA_MODEL_E4B_STATUS).orEmpty()
        currentModelVisionStatus = intent.getStringExtra(EXTRA_MODEL_VISION_STATUS).orEmpty()
        currentDocumentAnswerLength = intent.getStringExtra(EXTRA_DOCUMENT_ANSWER_LENGTH) ?: "short"
        currentRagSearchMode = intent.getStringExtra(EXTRA_RAG_SEARCH_MODE) ?: "auto"
        currentEmbeddingBackend = intent.getStringExtra(EXTRA_EMBEDDING_BACKEND) ?: "auto"
        currentPdfPageLimit = PdfSettings.getPdfPageLimit(this)
        embeddingModelStatus = intent.getStringExtra(EXTRA_EMBEDDING_MODEL_STATUS).orEmpty()
        embeddingIndexCount = intent.getIntExtra(EXTRA_EMBEDDING_INDEX_COUNT, 0)
        selectedDocumentTitle = intent.getStringExtra(EXTRA_SELECTED_DOCUMENT_TITLE)
        currentSessionTitle = intent.getStringExtra(EXTRA_SESSION_TITLE).orEmpty()
        appVersion = intent.getStringExtra(EXTRA_APP_VERSION).orEmpty()

 codex/improve-chat-usability
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        mainContainer = findViewById(R.id.sectionMainSettings)
        tvStatus = findViewById(R.id.tvSettingsStatusValue)
        tvModel = findViewById(R.id.tvSettingsModelValue)
        tvDoc = findViewById(R.id.tvSettingsDocValue)
        tvIndex = findViewById(R.id.tvSettingsIndexValue)
        tvVersion = findViewById(R.id.tvSettingsVersion)

        findViewById<View>(R.id.btnQuickReadiness).setOnClickListener {
            finishWithAction(ACTION_READINESS_CHECK)
        }
        findViewById<View>(R.id.btnQuickCopyReport).setOnClickListener {
            finishWithAction(ACTION_COPY_BETA_REPORT)
        }
        findViewById<View>(R.id.btnQuickPrivacy).setOnClickListener {
            finishWithAction(ACTION_PRIVACY_POLICY)
        }

        bindStateCard()
        populateMainSections()
    }

    private fun bindStateCard() {
        val status = currentModelStatus.ifBlank { "غير مشغّل" }
        tvStatus.text = status
        tvStatus.setBackgroundResource(statusBackground(status))
        tvStatus.setTextColor(statusTextColor(status))
        tvModel.text = "النموذج: ${currentModelDescription.ifBlank { "لم يتم اختيار نموذج" }}"
        tvDoc.text = selectedDocumentTitle?.let { "مستند: $it" } ?: "لا يوجد مستند نشط"
        tvDoc.setBackgroundResource(if (selectedDocumentTitle != null) R.drawable.bg_status_chip_ready else R.drawable.bg_status_chip_inactive)
        tvDoc.setTextColor(if (selectedDocumentTitle != null) ContextCompat.getColor(this, R.color.nabd_success) else ContextCompat.getColor(this, R.color.nabd_text_secondary))
        tvIndex.text = "$embeddingIndexCount فهرس"
        tvVersion.text = appVersionLabel()
    }

    private fun populateMainSections() {
        mainContainer.removeAllViews()
        addOptionRow(
            mainContainer,
            R.drawable.ic_help,
            "الحساب والتطبيق",
            "الذاكرة ${memoryStatusLabel()} • ${appVersionLabel()} • الخصوصية والتقارير",
            badge = "عام"
        ) {
            showAccountAppDialog()
        }

        addOptionRow(
            mainContainer,
            R.drawable.ic_model,
            "النماذج",
            modelSectionSummary(),
            badge = if (isPositiveStatus(currentModelStatus)) "جاهز" else "تحقق",
            badgeBackground = statusBackground(currentModelStatus)
        ) {
            showModelsDialog()
        }

        addOptionRow(
            mainContainer,
            R.drawable.ic_search,
            "المستندات والبحث",
            documentSectionSummary(),
            badge = if (selectedDocumentTitle != null) "نشط" else "${embeddingIndexCount} فهرس",
            badgeBackground = if (selectedDocumentTitle != null) R.drawable.bg_status_chip_ready else R.drawable.bg_status_chip_inactive
        ) {
            showDocumentsSearchDialog()
        }

        addOptionRow(
            mainContainer,
            R.drawable.ic_history,
            "المحادثات",
            "${currentSessionTitle.ifBlank { "محادثة نشطة" }} • نسخ أو بدء جلسة جديدة",
            badge = "جلسة"
        ) {
            showConversationsDialog()
        }

        addOptionRow(
            mainContainer,
            R.drawable.ic_tools,
            "الأدوات",
            "تشخيص الجهاز، مهام الخلفية، وأدوات الموقع المحفوظة",
            badge = "محلي"
        ) {
            showToolsDialog()

        val modelPickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            uri?.let { modelViewModel.setupModel(it) }
        }

        setContent {
            val modelState by modelViewModel.modelState.observeAsState(com.example.localqwen.viewmodel.ModelState.NotImported)
            val setupState by modelViewModel.setupState.observeAsState(ModelSetupState.Idle)
            var showDevMode by remember { mutableStateOf(false) }

            if (showDevMode) {
                ModelRuntimeDevScreen(onBackClick = { showDevMode = false })
            } else {
                NabdSettingsScreen(
                    appVersion = appVersion,
                    modelDescription = currentModelDescription,
                    modelStatus = currentModelStatus,
                    modelState = modelState,
                    onBackClick = { finish() },
                    onAccountClick = { showAccountAppDialog() },
                    onModelsClick = { showModelsDialog() },
                    onDocumentsClick = { showDocumentsSearchDialog() },
                    onChatsClick = { showConversationsDialog() },
                    onToolsClick = { showToolsDialog() },
                    onTermsClick = {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("شروط الخدمة")
                            .setMessage("شروط الخدمة الخاصة بتطبيق نبض (سيتم إضافتها لاحقاً).")
                            .setPositiveButton("حسناً", null)
                            .show()
                    },
                    onPrivacyClick = { finishWithAction(ACTION_PRIVACY_POLICY) },
                    onModelSettingsClick = { showModelsDialog() },
                    onCheckUpdatesClick = {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("التحقق من التحديثات")
                            .setMessage("أنت تستخدم أحدث إصدار متاح.")
                            .setPositiveButton("حسناً", null)
                            .show()
                    },
                    onSetupModel = { modelPickerLauncher.launch("*/*") },
                    onLoadModel = { modelViewModel.loadModel() },
                    onDevModeClick = { showDevMode = true }
                )
            }

            if (setupState !is ModelSetupState.Idle) {
                ModalBottomSheet(
                    onDismissRequest = { modelViewModel.resetSetupState() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    ModelSetupWizardSheet(
                        setupState = setupState,
                        onDismiss = { modelViewModel.resetSetupState() },
                        onRetry = { 
                            modelViewModel.resetSetupState()
                            modelPickerLauncher.launch("*/*") 
                        },
                        onStartChat = { 
                            modelViewModel.resetSetupState()
                            finish()
                        }
                    )
                }
            }
 main
        }
    }

    private fun showAccountAppDialog() {
        val memoryEnabledLabel = memoryStatusLabel()
        val items = arrayOf(
            "ذاكرة نبض ($memoryEnabledLabel)",
            "عرض الذاكرة",
            "مسح الذاكرة",
            "مساعدة نبض",
            "ما الجديد في نبض\nآخر تحسينات النسخة التجريبية",
            "إرسال ملاحظة للمطور\nاكتب مشكلة أو اقتراحًا للمساعدة في تحسين نبض",
            "إنشاء تقرير اختبار Gemma\nللتأكد من نظام تشخيص الأخطاء",
            "حول نبض",
            "سياسة الخصوصية",
            "نسخ تقرير بيتا"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("الحساب والتطبيق")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> finishWithAction(ACTION_TOGGLE_MEMORY)
                    1 -> finishWithAction(ACTION_SHOW_MEMORY)
                    2 -> confirmAction(
                        title = "مسح ذاكرة نبض؟",
                        message = "سيتم حذف عناصر الذاكرة المحفوظة على هذا الجهاز.",
                        action = ACTION_CLEAR_MEMORY
                    )
                    3 -> finishWithAction(ACTION_HELP)
                    4 -> finishWithAction(ACTION_WHATS_NEW)
                    5 -> finishWithAction(ACTION_SEND_FEEDBACK)
                    6 -> finishWithAction(ACTION_TRIGGER_TEST_REPORT)
                    7 -> finishWithAction(ACTION_ABOUT)
                    8 -> finishWithAction(ACTION_PRIVACY_POLICY)
                    9 -> finishWithAction(ACTION_COPY_BETA_REPORT)
                }
            }
            .show()
    }

    private fun showModelsDialog() {
        val items = arrayOf(
            "Gemma 3 Multimodal (${currentModelGemma3Status.ifBlank { "غير مستورد" }})",
            "Gemma E2B (${currentModelE2bStatus.ifBlank { "غير مستورد" }})",
            "Gemma E4B (${currentModelE4bStatus.ifBlank { "غير مستورد" }})",
 codex/improve-chat-usability
            "نموذج الرؤية (${currentModelVisionStatus.ifBlank { "غير مستورد" }})",
            "نموذج التضمين (${embeddingModelStatus.ifBlank { "غير مستورد" }})",

            "نموذج الرؤية الإضافي (FastVLM)",
            "نموذج التضمين",
 main
            "فحص الجاهزية\nتحقق من النماذج والمساحة قبل التشغيل",
            "دليل استيراد النماذج\nشرح سريع لاختيار واستيراد النماذج",
            "تشخيص نموذج الذكاء"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("النماذج")
            .setItems(items) { _, which ->
                when (which) {
 codex/improve-chat-usability
                    0 -> finishWithAction(ACTION_MANAGE_MODEL_E2B)
                    1 -> finishWithAction(ACTION_MANAGE_MODEL_E4B)
                    2 -> {
                        if (isPositiveStatus(currentModelVisionStatus)) {
                            confirmAction(
                                title = "حذف نموذج الرؤية؟",
                                message = "يمكنك استيراده مرة أخرى لاحقًا من ملف النموذج.",
                                action = ACTION_DELETE_VISION_MODEL
                            )

                    0 -> finishWithAction(ACTION_MANAGE_MODEL_GEMMA3)
                    1 -> finishWithAction(ACTION_MANAGE_MODEL_E2B)
                    2 -> finishWithAction(ACTION_MANAGE_MODEL_E4B)
                    3 -> {
                         if (currentModelVisionStatus.startsWith("مستورد")) {
                            finishWithAction(ACTION_DELETE_VISION_MODEL)
 main
                        } else {
                            finishWithAction(ACTION_IMPORT_VISION_MODEL)
                        }
                    }
                    4 -> showEmbeddingModelSubDialog()
                    5 -> finishWithAction(ACTION_READINESS_CHECK)
                    6 -> finishWithAction(ACTION_MODEL_IMPORT_HELP)
                    7 -> finishWithAction(ACTION_LITERT_DIAGNOSTICS)
                }
            }
            .show()
    }

    private fun showEmbeddingModelSubDialog() {
        val items = arrayOf("استيراد نموذج تضمين", "حذف نموذج التضمين", "محرك التضمين")
        MaterialAlertDialogBuilder(this)
            .setTitle("نموذج التضمين")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> finishWithAction(ACTION_IMPORT_EMBEDDING_MODEL)
                    1 -> confirmAction(
                        title = "حذف نموذج التضمين؟",
                        message = "سيتم حذف نموذج التضمين المحلي فقط، ولن تتأثر محادثاتك.",
                        action = ACTION_DELETE_EMBEDDING_MODEL
                    )
                    2 -> showEmbeddingBackendDialog()
                }
            }
            .show()
    }

    private fun showDocumentsSearchDialog() {
        val items = arrayOf(
            "مكتبة المستندات",
            "طول إجابة المستند (${documentAnswerLengthLabel(currentDocumentAnswerLength)})",
            "حد صفحات PDF (${pdfPageLimitLabel(currentPdfPageLimit)})",
            "وضع البحث (${ragSearchModeLabel(currentRagSearchMode)})",
            "إنشاء فهرس دلالي${selectedDocumentTitle?.let { "\n$it" } ?: "\nاختر مستندًا أولًا"}",
            "حذف الفهارس الدلالية ($embeddingIndexCount)",
            "تشخيص البحث الدلالي",
            if (selectedDocumentTitle != null) "إلغاء اختيار المستند\n$selectedDocumentTitle" else "لا يوجد مستند محدد"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("المستندات والبحث")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> finishWithAction(ACTION_OPEN_DOCUMENT_LIBRARY)
                    1 -> showDocumentAnswerLengthDialog()
                    2 -> showPdfPageLimitDialog()
                    3 -> showRagSearchModeDialog()
                    4 -> {
                        if (selectedDocumentTitle == null) {
                            showInfoDialog("لا يوجد مستند نشط", "أرفق PDF أو ملف نصي أولًا، ثم ارجع لإنشاء الفهرس الدلالي.")
                        } else {
                            finishWithAction(ACTION_BUILD_DOCUMENT_SEMANTIC_INDEX)
                        }
                    }
                    5 -> confirmAction(
                        title = "حذف الفهارس الدلالية؟",
                        message = "سيعاد بناؤها عند الحاجة من المستندات المحفوظة.",
                        action = ACTION_DELETE_EMBEDDING_INDEXES
                    )
                    6 -> finishWithAction(ACTION_RAG_DIAGNOSTICS)
                    7 -> {
                        if (selectedDocumentTitle == null) {
                            showInfoDialog("لا يوجد مستند محدد", "لا يوجد مستند نشط لإلغاء تحديده.")
                        } else {
                            finishWithAction(ACTION_CLEAR_SELECTED_DOCUMENT)
                        }
                    }
                }
            }
            .show()
    }

    private fun showConversationsDialog() {
        val items = arrayOf(
            "سجل المحادثات",
            "نسخ المحادثة",
            "نسخ آخر رد",
            "مسح المحادثة الحالية"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("المحادثات")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> finishWithAction(ACTION_OPEN_CHAT_HISTORY)
                    1 -> finishWithAction(ACTION_COPY_CHAT)
                    2 -> finishWithAction(ACTION_COPY_LAST_RESPONSE)
                    3 -> confirmAction(
                        title = "مسح المحادثة الحالية؟",
                        message = "سيتم بدء محادثة جديدة مع حفظ السجل المحلي حسب إعدادات التطبيق.",
                        action = ACTION_CLEAR_CHAT
                    )
                }
            }
            .show()
    }

    private fun showToolsDialog() {
        val items = arrayOf(
            "أدوات الهاتف (البطارية، الجهاز)",
            "أداة الخريطة",
            "الأماكن المحفوظة",
            "مهام الخلفية"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("الأدوات")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> finishWithAction(ACTION_LOCAL_MODEL_MANAGER) // Using manager for tools info
                    1 -> showInfoDialog("أداة الخريطة", "واجهة إدارة الخريطة والأماكن يمكن إضافتها كشاشة مستقلة في المرحلة القادمة.")
                    2 -> showInfoDialog("الأماكن المحفوظة", "الأماكن المحفوظة تبقى محلية على الجهاز، وسيظهر مديرها هنا عند اكتمال الواجهة.")
                    3 -> finishWithAction(ACTION_BACKGROUND_TASKS)
                }
            }
            .show()
    }

    private fun showDocumentAnswerLengthDialog() {
        val values = arrayOf("short", "medium", "long")
        val labels = values.map { documentAnswerLengthLabel(it) }.toTypedArray()
        val selectedIndex = values.indexOf(currentDocumentAnswerLength).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("طول إجابة المستند")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                dialog.dismiss()
                finishWithAction(ACTION_SET_DOCUMENT_ANSWER_LENGTH, values[which])
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showRagSearchModeDialog() {
        val values = arrayOf("auto", "keyword", "semantic")
        val labels = values.map { ragSearchModeLabel(it) }.toTypedArray()
        val selectedIndex = values.indexOf(currentRagSearchMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("وضع البحث في المستندات")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                dialog.dismiss()
                finishWithAction(ACTION_SET_RAG_SEARCH_MODE, values[which])
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showPdfPageLimitDialog() {
        val limits = intArrayOf(3, 10, 25, 50)
        val labels = limits.map { pdfPageLimitLabel(it) }.toTypedArray()
        val selectedIndex = limits.indexOf(currentPdfPageLimit).let { if (it >= 0) it else 1 }
        MaterialAlertDialogBuilder(this)
            .setTitle("حد صفحات PDF")
            .setMessage("عدد الصفحات التي يحللها نبض من ملفات PDF")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                dialog.dismiss()
                finishWithAction(ACTION_SET_PDF_PAGE_LIMIT, limits[which].toString())
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showEmbeddingBackendDialog() {
        val values = arrayOf("auto", "mediapipe", "tflite")
        val labels = values.map { embeddingBackendLabel(it) }.toTypedArray()
        val selectedIndex = values.indexOf(currentEmbeddingBackend).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("محرك التضمين")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                dialog.dismiss()
                finishWithAction(ACTION_SET_EMBEDDING_BACKEND, values[which])
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmAction(title: String, message: String, action: String, value: String? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تأكيد") { _, _ -> finishWithAction(action, value) }
            .show()
    }

    private fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun memoryStatusLabel(): String {
        return if (MemoryStore(this).isMemoryEnabled()) "مفعّلة" else "معطّلة"
    }

    private fun appVersionLabel(): String {
        return if (appVersion.isBlank()) "نسخة تجريبية" else "v$appVersion"
    }

    private fun modelSectionSummary(): String {
        val selected = currentModelDescription.ifBlank { "لم يتم اختيار نموذج" }
        val status = currentModelStatus.ifBlank { "غير مشغّل" }
        return "$selected • $status • الرؤية ${currentModelVisionStatus.ifBlank { "غير مستوردة" }}"
    }

    private fun documentSectionSummary(): String {
        val document = selectedDocumentTitle ?: "لا يوجد مستند نشط"
        return "$document • ${ragSearchModeLabel(currentRagSearchMode)} • ${pdfPageLimitLabel(currentPdfPageLimit)} • $embeddingIndexCount فهرس"
    }

    private fun isPositiveStatus(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized.contains("غير")) return false
        return normalized.contains("جاهز") || normalized.contains("مستورد") || normalized.contains("Ready", ignoreCase = true)
    }

    private fun statusBackground(value: String): Int {
        val normalized = value.trim()
        return when {
            isPositiveStatus(normalized) -> R.drawable.bg_status_chip_ready
            normalized.contains("جاري") || normalized.contains("تحميل") || normalized.contains("Loading", ignoreCase = true) -> R.drawable.bg_status_chip_loading
            else -> R.drawable.bg_status_chip_inactive
        }
    }

    private fun statusTextColor(value: String): Int {
        return when (statusBackground(value)) {
            R.drawable.bg_status_chip_ready -> ContextCompat.getColor(this, R.color.nabd_success)
            R.drawable.bg_status_chip_loading -> ContextCompat.getColor(this, R.color.nabd_primary)
            else -> ContextCompat.getColor(this, R.color.nabd_text_secondary)
        }
    }

    private fun documentAnswerLengthLabel(value: String): String {
        return when (value) {
            "medium" -> "متوسط"
            "long" -> "مفصل"
            else -> "مختصر"
        }
    }

    private fun ragSearchModeLabel(value: String): String {
        return when (value) {
            "keyword" -> "بحث نصي"
            "semantic" -> "بحث دلالي"
            else -> "تلقائي"
        }
    }

    private fun pdfPageLimitLabel(limit: Int): String {
        return "$limit صفحات"
    }

    private fun embeddingBackendLabel(value: String): String {
        return when (value) {
            "mediapipe" -> "MediaPipe"
            "tflite" -> "TensorFlow Lite"
            else -> "تلقائي"
        }
    }

 codex/improve-chat-usability
    private fun addOptionRow(
        container: LinearLayout,
        iconRes: Int,
        title: String,
        subtitle: String? = null,
        titleColor: Int = ContextCompat.getColor(this, R.color.nabd_on_surface),
        iconColor: Int = ContextCompat.getColor(this, R.color.nabd_text_secondary),
        badge: String? = null,
        badgeBackground: Int = R.drawable.bg_status_chip_inactive,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_option_row, container, false)
        row.findViewById<ImageView>(R.id.ivOptionIcon).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconColor)
        }
        row.findViewById<TextView>(R.id.tvOptionTitle).apply {
            text = title
            setTextColor(titleColor)
        }
        row.findViewById<TextView>(R.id.tvOptionSubtitle).apply {
            if (subtitle.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                text = subtitle
                visibility = View.VISIBLE
            }
        }
        row.findViewById<TextView>(R.id.tvOptionBadge).apply {
            if (badge.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                text = badge
                setBackgroundResource(badgeBackground)
                setTextColor(
                    when (badgeBackground) {
                        R.drawable.bg_status_chip_ready -> ContextCompat.getColor(this@SettingsActivity, R.color.nabd_success)
                        R.drawable.bg_status_chip_loading -> ContextCompat.getColor(this@SettingsActivity, R.color.nabd_primary)
                        else -> ContextCompat.getColor(this@SettingsActivity, R.color.nabd_text_secondary)
                    }
                )
                visibility = View.VISIBLE
            }
        }
        row.setOnClickListener { onClick() }
        container.addView(row)
    }


 main
    private fun finishWithAction(action: String, value: String? = null) {
        val intent = Intent().putExtra(EXTRA_ACTION, action)
        if (value != null) intent.putExtra(EXTRA_VALUE, value)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "settings_action"
        const val EXTRA_VALUE = "settings_value"
        const val EXTRA_MODEL_DESCRIPTION = "model_description"
        const val EXTRA_MODEL_STATUS = "model_status"
        const val EXTRA_MODEL_GEMMA3_STATUS = "model_gemma3_status"
        const val EXTRA_MODEL_E2B_STATUS = "model_e2b_status"
        const val EXTRA_MODEL_E4B_STATUS = "model_e4b_status"
        const val EXTRA_MODEL_VISION_STATUS = "model_vision_status"
        const val EXTRA_DOCUMENT_ANSWER_LENGTH = "document_answer_length"
        const val EXTRA_RAG_SEARCH_MODE = "rag_search_mode"
        const val EXTRA_EMBEDDING_BACKEND = "embedding_backend"
        const val EXTRA_EMBEDDING_MODEL_STATUS = "embedding_model_status"
        const val EXTRA_EMBEDDING_INDEX_COUNT = "embedding_index_count"
        const val EXTRA_SELECTED_DOCUMENT_TITLE = "selected_document_title"
        const val EXTRA_SESSION_TITLE = "session_title"
        const val EXTRA_APP_VERSION = "app_version"

        const val ACTION_SELECT_MODEL = "select_model"
        const val ACTION_IMPORT_MODEL = "import_model"
        const val ACTION_MANAGE_MODEL_GEMMA3 = "manage_model_gemma3"
        const val ACTION_MANAGE_MODEL_E2B = "manage_model_e2b"
        const val ACTION_MANAGE_MODEL_E4B = "manage_model_e4b"
        const val ACTION_IMPORT_VISION_MODEL = "import_vision_model"
        const val ACTION_DELETE_VISION_MODEL = "delete_vision_model"
        const val ACTION_LITERT_DIAGNOSTICS = "litert_diagnostics"
        const val ACTION_READINESS_CHECK = "readiness_check"
        const val ACTION_MODEL_IMPORT_HELP = "model_import_help"
        const val ACTION_IMPORT_EMBEDDING_MODEL = "import_embedding_model"
        const val ACTION_DELETE_EMBEDDING_MODEL = "delete_embedding_model"
        const val ACTION_DELETE_EMBEDDING_INDEXES = "delete_embedding_indexes"
        const val ACTION_BUILD_DOCUMENT_SEMANTIC_INDEX = "build_document_semantic_index"
        const val ACTION_RAG_DIAGNOSTICS = "rag_diagnostics"
        const val ACTION_SET_DOCUMENT_ANSWER_LENGTH = "set_document_answer_length"
        const val ACTION_SET_PDF_PAGE_LIMIT = "set_pdf_page_limit"
        const val ACTION_SET_RAG_SEARCH_MODE = "set_rag_search_mode"
        const val ACTION_SET_EMBEDDING_BACKEND = "set_embedding_backend"
        const val ACTION_CLEAR_SELECTED_DOCUMENT = "clear_selected_document"
        const val ACTION_CLEAR_CHAT = "clear_chat"
        const val ACTION_COPY_CHAT = "copy_chat"
        const val ACTION_COPY_LAST_RESPONSE = "copy_last_response"
        const val ACTION_OPEN_CHAT_HISTORY = "open_chat_history"
        const val ACTION_OPEN_DOCUMENT_LIBRARY = "open_document_library"
        const val ACTION_BACKGROUND_TASKS = "background_tasks"
        const val ACTION_LOCAL_MODEL_MANAGER = "local_model_manager"
        const val ACTION_COPY_BETA_REPORT = "copy_beta_report"
        const val ACTION_WHATS_NEW = "whats_new"
        const val ACTION_SEND_FEEDBACK = "send_feedback"
        const val ACTION_TRIGGER_TEST_REPORT = "trigger_test_report"
        const val ACTION_TOGGLE_MEMORY = "toggle_memory"
        const val ACTION_SHOW_MEMORY = "show_memory"
        const val ACTION_CLEAR_MEMORY = "clear_memory"
        const val ACTION_HELP = "help"
        const val ACTION_ABOUT = "about"
        const val ACTION_PRIVACY_POLICY = "privacy_policy"

        fun createIntent(
            context: Context,
            modelDescription: String,
            modelStatus: String,
            modelGemma3Status: String,
            modelE2bStatus: String,
            modelE4bStatus: String,
            modelVisionStatus: String,
            documentAnswerLength: String,
            ragSearchMode: String,
            embeddingBackend: String,
            embeddingModelStatus: String,
            embeddingIndexCount: Int,
            selectedDocumentTitle: String?,
            sessionTitle: String,
            appVersion: String
        ): Intent {
            return Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_MODEL_DESCRIPTION, modelDescription)
                .putExtra(EXTRA_MODEL_STATUS, modelStatus)
                .putExtra(EXTRA_MODEL_GEMMA3_STATUS, modelGemma3Status)
                .putExtra(EXTRA_MODEL_E2B_STATUS, modelE2bStatus)
                .putExtra(EXTRA_MODEL_E4B_STATUS, modelE4bStatus)
                .putExtra(EXTRA_MODEL_VISION_STATUS, modelVisionStatus)
                .putExtra(EXTRA_DOCUMENT_ANSWER_LENGTH, documentAnswerLength)
                .putExtra(EXTRA_RAG_SEARCH_MODE, ragSearchMode)
                .putExtra(EXTRA_EMBEDDING_BACKEND, embeddingBackend)
                .putExtra(EXTRA_EMBEDDING_MODEL_STATUS, embeddingModelStatus)
                .putExtra(EXTRA_EMBEDDING_INDEX_COUNT, embeddingIndexCount)
                .putExtra(EXTRA_SELECTED_DOCUMENT_TITLE, selectedDocumentTitle)
                .putExtra(EXTRA_SESSION_TITLE, sessionTitle)
                .putExtra(EXTRA_APP_VERSION, appVersion)
        }
    }
}
