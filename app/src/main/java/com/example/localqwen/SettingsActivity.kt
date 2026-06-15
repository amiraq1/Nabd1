package com.example.localqwen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.localqwen.document.PdfSettings
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

import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.example.localqwen.viewmodel.ModelViewModel

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private val modelViewModel: ModelViewModel by viewModels()

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
    private var selectedDocumentTitle: String? = null
    private var currentSessionTitle: String = ""
    private var appVersion: String = ""

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                com.example.localqwen.ui.settings.SettingsScreen(
                    modelState = modelState,
                    modelName = currentModelDescription,
                    onBackClick = { finish() },
                    onImportModelClick = { modelPickerLauncher.launch("*/*") }
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
        }
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
