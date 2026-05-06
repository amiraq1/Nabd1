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
    private var currentDocumentAnswerLength: String = "short"
    private var selectedDocumentTitle: String? = null
    private var currentSessionTitle: String = ""
    private var appVersion: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        currentModelDescription = intent.getStringExtra(EXTRA_MODEL_DESCRIPTION).orEmpty()
        currentModelStatus = intent.getStringExtra(EXTRA_MODEL_STATUS).orEmpty()
        currentDocumentAnswerLength = intent.getStringExtra(EXTRA_DOCUMENT_ANSWER_LENGTH) ?: "short"
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
            "اختيار النموذج",
            if (currentModelStatus.isNotBlank()) "$currentModelDescription\nالحالة: $currentModelStatus" else currentModelDescription
        ) {
            finishWithAction(ACTION_SELECT_MODEL)
        }
        addOptionRow(sectionModel, "⇩", "استيراد النموذج", "استيراد ملف النموذج المحلي") {
            finishWithAction(ACTION_IMPORT_MODEL)
        }

        addOptionRow(
            sectionDocuments,
            "≡",
            "طول إجابة المستند",
            documentAnswerLengthLabel(currentDocumentAnswerLength)
        ) {
            showDocumentAnswerLengthDialog()
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

    private fun documentAnswerLengthLabel(value: String): String {
        return when (value) {
            "medium" -> "متوسط"
            "long" -> "مفصل"
            else -> "مختصر"
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
        const val EXTRA_DOCUMENT_ANSWER_LENGTH = "document_answer_length"
        const val EXTRA_SELECTED_DOCUMENT_TITLE = "selected_document_title"
        const val EXTRA_SESSION_TITLE = "session_title"
        const val EXTRA_APP_VERSION = "app_version"

        const val ACTION_SELECT_MODEL = "select_model"
        const val ACTION_IMPORT_MODEL = "import_model"
        const val ACTION_SET_DOCUMENT_ANSWER_LENGTH = "set_document_answer_length"
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
            documentAnswerLength: String,
            selectedDocumentTitle: String?,
            sessionTitle: String,
            appVersion: String
        ): Intent {
            return Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_MODEL_DESCRIPTION, modelDescription)
                .putExtra(EXTRA_MODEL_STATUS, modelStatus)
                .putExtra(EXTRA_DOCUMENT_ANSWER_LENGTH, documentAnswerLength)
                .putExtra(EXTRA_SELECTED_DOCUMENT_TITLE, selectedDocumentTitle)
                .putExtra(EXTRA_SESSION_TITLE, sessionTitle)
                .putExtra(EXTRA_APP_VERSION, appVersion)
        }
    }
}
