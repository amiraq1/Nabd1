package com.example.localqwen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var sectionModel: LinearLayout
    private lateinit var sectionDocuments: LinearLayout
    private lateinit var sectionConversation: LinearLayout
    private lateinit var sectionApp: LinearLayout

    private var currentModelDescription: String = ""
    private var currentModelStatus: String = ""
    private var currentModelE2bStatus: String = ""
    private var currentModelE4bStatus: String = ""
    private var currentDocumentAnswerLength: String = "short"
    private var currentRagSearchMode: String = "auto"
    private var currentEmbeddingBackend: String = "auto"
    private var embeddingModelStatus: String = ""
    private var embeddingIndexCount: Int = 0
    private var selectedDocumentTitle: String? = null
    private var currentSessionTitle: String = ""
    private var appVersion: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        currentModelDescription = intent.getStringExtra(EXTRA_MODEL_DESCRIPTION).orEmpty()
        currentModelStatus = intent.getStringExtra(EXTRA_MODEL_STATUS).orEmpty()
        currentModelE2bStatus = intent.getStringExtra(EXTRA_MODEL_E2B_STATUS).orEmpty()
        currentModelE4bStatus = intent.getStringExtra(EXTRA_MODEL_E4B_STATUS).orEmpty()
        currentDocumentAnswerLength = intent.getStringExtra(EXTRA_DOCUMENT_ANSWER_LENGTH) ?: "short"
        currentRagSearchMode = intent.getStringExtra(EXTRA_RAG_SEARCH_MODE) ?: "auto"
        currentEmbeddingBackend = intent.getStringExtra(EXTRA_EMBEDDING_BACKEND) ?: "auto"
        embeddingModelStatus = intent.getStringExtra(EXTRA_EMBEDDING_MODEL_STATUS).orEmpty()
        embeddingIndexCount = intent.getIntExtra(EXTRA_EMBEDDING_INDEX_COUNT, 0)
        selectedDocumentTitle = intent.getStringExtra(EXTRA_SELECTED_DOCUMENT_TITLE)
        currentSessionTitle = intent.getStringExtra(EXTRA_SESSION_TITLE).orEmpty()
        appVersion = intent.getStringExtra(EXTRA_APP_VERSION).orEmpty()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        sectionModel = findViewById(R.id.sectionModelSettings)
        sectionDocuments = findViewById(R.id.sectionDocumentsSettings)
        sectionConversation = findViewById(R.id.sectionConversationSettings)
        sectionApp = findViewById(R.id.sectionAppSettings)

        bindStateCard()
        populateSettings()
    }

    private fun bindStateCard() {
        findViewById<TextView>(R.id.tvCurrentSessionValue).text =
            currentSessionTitle.ifBlank { "محادثة جديدة" }
        findViewById<TextView>(R.id.tvCurrentDocumentValue).text =
            selectedDocumentTitle ?: "لا يوجد مستند محدد"
        findViewById<TextView>(R.id.tvCurrentModelValue).text =
            "$currentModelDescription • ${currentModelStatus.ifBlank { "غير معروف" }}"

        findViewById<View>(R.id.rowCurrentSession).apply {
            contentDescription = "فتح سجل المحادثات"
            setOnClickListener { finishWithAction(ACTION_OPEN_CHAT_HISTORY) }
        }
        findViewById<View>(R.id.rowCurrentDocument).apply {
            contentDescription = "فتح مكتبة المستندات"
            setOnClickListener { finishWithAction(ACTION_OPEN_DOCUMENT_LIBRARY) }
        }
        findViewById<View>(R.id.rowCurrentModel).apply {
            contentDescription = "اختيار النموذج"
            setOnClickListener { finishWithAction(ACTION_SELECT_MODEL) }
        }
    }

    private fun populateSettings() {
        addOptionRow(
            sectionModel,
            "◉",
            "النموذج الحالي",
            buildString {
                append(currentModelDescription.ifBlank { "غير محدد" })
                append("\nالحالة: ")
                append(currentModelStatus.ifBlank { "غير معروف" })
            }
        ) {
            finishWithAction(ACTION_SELECT_MODEL)
        }
        addOptionRow(
            sectionModel,
            "E",
            "Gemma E2B",
            currentModelE2bStatus.ifBlank { "غير مستورد" }
        ) {
            finishWithAction(ACTION_MANAGE_MODEL_E2B)
        }
        addOptionRow(
            sectionModel,
            "E",
            "Gemma E4B",
            currentModelE4bStatus.ifBlank { "غير مستورد" }
        ) {
            finishWithAction(ACTION_MANAGE_MODEL_E4B)
        }

        addOptionRow(
            sectionDocuments,
            "≡",
            "طول إجابة المستند",
            documentAnswerLengthLabel(currentDocumentAnswerLength)
        ) {
            showDocumentAnswerLengthDialog()
        }
        addOptionRow(
            sectionDocuments,
            "⌕",
            "وضع البحث في المستندات",
            ragSearchModeLabel(currentRagSearchMode)
        ) {
            showRagSearchModeDialog()
        }
        addOptionRow(
            sectionDocuments,
            "⬢",
            "نموذج التضمين",
            embeddingModelStatus.ifBlank { "غير مستورد" }
        ) {
            finishWithAction(ACTION_RAG_DIAGNOSTICS)
        }
        addOptionRow(
            sectionDocuments,
            "⇩",
            "استيراد نموذج التضمين",
            if (embeddingModelStatus.startsWith("مستورد")) {
                "استبدال النموذج الحالي"
            } else {
                "استيراد ملف .tflite"
            }
        ) {
            finishWithAction(ACTION_IMPORT_EMBEDDING_MODEL)
        }
        if (embeddingModelStatus.startsWith("مستورد")) {
            addOptionRow(
                sectionDocuments,
                "⌫",
                "حذف نموذج التضمين",
                "إزالة النموذج المحلي والإبقاء على البحث النصي"
            ) {
                finishWithAction(ACTION_DELETE_EMBEDDING_MODEL)
            }
        }
        addOptionRow(
            sectionDocuments,
            "⚙",
            "محرك التضمين",
            embeddingBackendLabel(currentEmbeddingBackend)
        ) {
            showEmbeddingBackendDialog()
        }
        addOptionRow(
            sectionDocuments,
            "≈",
            "إنشاء فهرس دلالي للمستند",
            selectedDocumentTitle ?: "اختر مستندًا أولًا"
        ) {
            finishWithAction(ACTION_BUILD_DOCUMENT_SEMANTIC_INDEX)
        }
        if (embeddingIndexCount > 0) {
            addOptionRow(
                sectionDocuments,
                "⌦",
                "حذف الفهارس الدلالية",
                "عدد الفهارس: $embeddingIndexCount"
            ) {
                finishWithAction(ACTION_DELETE_EMBEDDING_INDEXES)
            }
        }
        addOptionRow(
            sectionDocuments,
            "؟",
            "تشخيص البحث الدلالي",
            "حالة نموذج التضمين والفهرس"
        ) {
            finishWithAction(ACTION_RAG_DIAGNOSTICS)
        }

        if (!selectedDocumentTitle.isNullOrBlank()) {
            addOptionRow(
                sectionDocuments,
                "×",
                "إلغاء اختيار المستند",
                selectedDocumentTitle
            ) {
                finishWithAction(ACTION_CLEAR_SELECTED_DOCUMENT)
            }
        }

        addOptionRow(
            sectionConversation,
            "×",
            "مسح المحادثة الحالية",
            titleColor = ContextCompat.getColor(this, R.color.nabd_error),
            iconColor = ContextCompat.getColor(this, R.color.nabd_error)
        ) {
            finishWithAction(ACTION_CLEAR_CHAT)
        }
        addOptionRow(sectionConversation, "⧉", "نسخ المحادثة") {
            finishWithAction(ACTION_COPY_CHAT)
        }
        addOptionRow(sectionConversation, "⧉", "نسخ آخر رد") {
            finishWithAction(ACTION_COPY_LAST_RESPONSE)
        }

        addOptionRow(
            sectionApp,
            "؟",
            "حول نبض",
            "الإصدار: $appVersion"
        ) {
            finishWithAction(ACTION_ABOUT)
        }
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

    private fun embeddingBackendLabel(value: String): String {
        return when (value) {
            "mediapipe" -> "MediaPipe"
            "tflite" -> "TensorFlow Lite"
            else -> "تلقائي"
        }
    }

    private fun addOptionRow(
        container: LinearLayout,
        icon: String,
        title: String,
        subtitle: String? = null,
        titleColor: Int = ContextCompat.getColor(this, R.color.nabd_on_surface),
        iconColor: Int = ContextCompat.getColor(this, R.color.nabd_text_secondary),
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_option_row, container, false)
        row.findViewById<TextView>(R.id.tvOptionIcon).apply {
            text = icon
            setTextColor(iconColor)
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
        row.setOnClickListener { onClick() }
        container.addView(row)
    }

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
        const val EXTRA_MODEL_E2B_STATUS = "model_e2b_status"
        const val EXTRA_MODEL_E4B_STATUS = "model_e4b_status"
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
        const val ACTION_MANAGE_MODEL_E2B = "manage_model_e2b"
        const val ACTION_MANAGE_MODEL_E4B = "manage_model_e4b"
        const val ACTION_IMPORT_EMBEDDING_MODEL = "import_embedding_model"
        const val ACTION_DELETE_EMBEDDING_MODEL = "delete_embedding_model"
        const val ACTION_DELETE_EMBEDDING_INDEXES = "delete_embedding_indexes"
        const val ACTION_BUILD_DOCUMENT_SEMANTIC_INDEX = "build_document_semantic_index"
        const val ACTION_RAG_DIAGNOSTICS = "rag_diagnostics"
        const val ACTION_SET_DOCUMENT_ANSWER_LENGTH = "set_document_answer_length"
        const val ACTION_SET_RAG_SEARCH_MODE = "set_rag_search_mode"
        const val ACTION_SET_EMBEDDING_BACKEND = "set_embedding_backend"
        const val ACTION_CLEAR_SELECTED_DOCUMENT = "clear_selected_document"
        const val ACTION_CLEAR_CHAT = "clear_chat"
        const val ACTION_COPY_CHAT = "copy_chat"
        const val ACTION_COPY_LAST_RESPONSE = "copy_last_response"
        const val ACTION_OPEN_CHAT_HISTORY = "open_chat_history"
        const val ACTION_OPEN_DOCUMENT_LIBRARY = "open_document_library"
        const val ACTION_ABOUT = "about"

        fun createIntent(
            context: Context,
            modelDescription: String,
            modelStatus: String,
            modelE2bStatus: String,
            modelE4bStatus: String,
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
                .putExtra(EXTRA_MODEL_E2B_STATUS, modelE2bStatus)
                .putExtra(EXTRA_MODEL_E4B_STATUS, modelE4bStatus)
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
