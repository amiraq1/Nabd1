package com.example.localqwen.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.Companion.SUPPORTED_MODELS
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.Role
import com.example.localqwen.viewmodel.ChatViewModel
import com.example.localqwen.viewmodel.ChatUiState
import com.example.localqwen.viewmodel.ModelViewModel
import com.example.localqwen.viewmodel.ModelState
import com.example.localqwen.viewmodel.MemoryViewModel
import com.example.localqwen.viewmodel.StatusEvent
import com.example.localqwen.viewmodel.MemoryCommandResult

import androidx.compose.ui.platform.LocalContext
import com.example.localqwen.diagnostics.NabdDiagnosticLogger
import android.content.Intent

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NabdApp(
    chatViewModel: ChatViewModel,
    modelViewModel: ModelViewModel,
    memoryViewModel: MemoryViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by chatViewModel.uiState.collectAsState(initial = ChatUiState())
    val messages = uiState.chatHistory
    val chatState = uiState.state
    val isBusy = chatState == com.example.localqwen.viewmodel.ChatState.GENERATING || chatState == com.example.localqwen.viewmodel.ChatState.PREPARING_CONTEXT || uiState.isProcessingDocument
    val statusEvent = uiState.statusEvent
    val lastErrorReport = uiState.lastErrorReportFile

    val selectedModel by modelViewModel.selectedModel.observeAsState()
    val modelState by modelViewModel.modelState.observeAsState()
    val modelStatusEvent by modelViewModel.statusEvent.observeAsState()
    val performanceState by modelViewModel.performanceState.observeAsState(com.example.localqwen.viewmodel.PerformanceState())
    
    var statusText by remember { mutableStateOf("جاهز") }
    var showClearMemoryConfirm by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    if (lastErrorReport != null) {
        AlertDialog(
            onDismissRequest = { chatViewModel.clearErrorReport() },
            title = { 
                Text(
                    "تقرير تشخيص Gemma 3",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    style = TextStyle(textDirection = TextDirection.Rtl)
                ) 
            },
            text = { 
                Column {
                    Text(
                        "تعذر تشغيل النموذج. تم حفظ تقرير تقني مفصل في المسار التالي:",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right,
                        style = TextStyle(textDirection = TextDirection.Rtl)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            lastErrorReport.absolutePath,
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            modifier = Modifier
                                .background(Color(0xFFF0F0F0), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "يمكنك مشاركة هذا الملف مع المطورين للمساعدة في حل المشكلة.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right,
                        style = TextStyle(textDirection = TextDirection.Rtl)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val uri = NabdDiagnosticLogger.getLogFileUri(context, lastErrorReport)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير نبض"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "فشل بدء المشاركة: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("مشاركة الملف")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(lastErrorReport.absolutePath))
                        Toast.makeText(context, "تم نسخ المسار", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("نسخ المسار")
                    }
                    TextButton(onClick = { chatViewModel.clearErrorReport() }) {
                        Text("إغلاق")
                    }
                }
            }
        )
    }

    LaunchedEffect(modelState) {
        modelState?.let { state ->
            statusText = when (state) {
                is com.example.localqwen.viewmodel.ModelState.Loading -> {
                    if (state.progress != null) {
                        "جاري التحميل (${(state.progress * 100).toInt()}%)..."
                    } else {
                        "جاري تشغيل نبض..."
                    }
                }
                is com.example.localqwen.viewmodel.ModelState.Ready -> "جاهز"
                is com.example.localqwen.viewmodel.ModelState.Idle -> "غير مشغّل"
                is com.example.localqwen.viewmodel.ModelState.NotImported -> "غير مستورد"
                is com.example.localqwen.viewmodel.ModelState.Error -> state.message
            }
        }
    }

    LaunchedEffect(statusEvent, modelStatusEvent) {
        val activeEvent = statusEvent ?: modelStatusEvent
        activeEvent?.let { event ->
            statusText = when (event) {
                is StatusEvent.Info -> event.message
                is StatusEvent.Success -> event.message
                is StatusEvent.Error -> event.message
            }
        }
    }

    val setupState by modelViewModel.setupState.observeAsState(com.example.localqwen.viewmodel.ModelSetupState.Idle)

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { modelViewModel.setupModel(it) }
    }

    if (setupState !is com.example.localqwen.viewmodel.ModelSetupState.Idle) {
        ModalBottomSheet(
            onDismissRequest = { modelViewModel.resetSetupState() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            ModelSetupWizardSheet(
                setupState = setupState!!,
                onDismiss = { modelViewModel.resetSetupState() },
                onRetry = { 
                    modelViewModel.resetSetupState()
                    modelPickerLauncher.launch("*/*") 
                },
                onStartChat = { modelViewModel.resetSetupState() }
            )
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { chatViewModel.importDocument(it) }
    }

    // New Image Picker for Gemma 3 Vision
    var showVisionDialog by remember { mutableStateOf(false) }
    var selectedVisionUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVisionUri = it
            showVisionDialog = true
        }
    }

    if (showVisionDialog && selectedVisionUri != null) {
        var visionPrompt by remember { mutableStateOf("اشرح لي هذه الصورة") }
        AlertDialog(
            onDismissRequest = { showVisionDialog = false },
            title = { Text("تحليل الصورة") },
            text = {
                OutlinedTextField(
                    value = visionPrompt,
                    onValueChange = { visionPrompt = it },
                    label = { Text("السؤال") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showVisionDialog = false
                    modelViewModel.clearTpsHistory()
                    chatViewModel.analyzeImageWithGemma(selectedVisionUri!!, visionPrompt)
                    selectedVisionUri = null
                }) {
                    Text("إرسال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVisionDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    val responseMode by modelViewModel.responseMode.observeAsState("balanced")


    val handleSendMessage: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            val memoryResult = memoryViewModel.handleMemoryCommand(text)
            if (memoryResult != null) {
                when (memoryResult) {
                    is MemoryCommandResult.ShowList -> {
                        chatViewModel.addSystemMessage(memoryResult.text)
                    }
                    is MemoryCommandResult.ConfirmClear -> {
                        showClearMemoryConfirm = true
                    }
                    is MemoryCommandResult.Success -> {
                        chatViewModel.addSystemMessage(memoryResult.message)
                    }
                    is MemoryCommandResult.Message -> {
                        chatViewModel.addSystemMessage(memoryResult.message)
                    }
                    is MemoryCommandResult.Error -> {
                        chatViewModel.addSystemMessage("خطأ: ${memoryResult.message}")
                    }
                }
            } else {
                modelViewModel.clearTpsHistory()
                chatViewModel.sendMessage(
                    input = text,
                    engine = modelViewModel.textInferenceEngine,
                    embeddingEngine = modelViewModel.embeddingEngine,
                    embeddingStore = modelViewModel.embeddingStore,
                    semanticRetriever = modelViewModel.semanticRetriever,
                    ragMode = modelViewModel.currentRagMode(),
                    documentAnswerLengthInstruction = chatViewModel.currentDocumentAnswerLength(),
                    memoryContext = memoryViewModel.buildMemoryContextForPrompt(),
                    responseMode = responseMode
                )
            }
        }
    }

    if (messages.isEmpty()) {
        val memories by memoryViewModel.memories.observeAsState(emptyList())
        var showMemoryDialog by remember { mutableStateOf(false) }

        NabdWelcomeScreen(
            activeModelName = selectedModel?.displayName ?: "Gemma",
            modelState = modelState ?: ModelState.NotImported,
            onSendMessage = handleSendMessage,
            onAddAttachment = { documentPickerLauncher.launch("*/*") },
            onAnalyzeImage = { imagePickerLauncher.launch("image/*") },
            onShowHistory = { showMemoryDialog = true },
            onShowMenu = onOpenSettings,
            onModelBadgeClick = { showModelSheet = true },
            isBusy = isBusy,
            statusText = statusText,
            onSetupModel = { modelPickerLauncher.launch("*/*") },
            onLoadModel = { modelViewModel.loadModel() }
        )

        if (showMemoryDialog) {
            MemoryDialog(
                memories = memories,
                onDismiss = { showMemoryDialog = false },
                onDelete = { memoryViewModel.deleteMemory(it) },
                onEdit = { id, text -> memoryViewModel.updateMemory(id, text) }
            )
        }
    } else {
        ChatScreen(
            messages = messages,
            isGenerating = isBusy,
            onSendMessage = handleSendMessage,
            onAddAttachment = { documentPickerLauncher.launch("*/*") },
            onCancelGeneration = { chatViewModel.stopGeneration() },
            onMenuClick = onOpenSettings,
            onBackClick = { chatViewModel.startNewChat() },
            statusText = statusText,
            onModelClick = { showModelSheet = true }
        )
    }

    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            scrimColor = Color.Black.copy(alpha = 0.25f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "الأداء والتحكم",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = TextStyle(
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Right
                    )
                )

                // RAM Usage Monitor
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("استهلاك الذاكرة (RAM)", fontSize = 14.sp, color = Color.Gray)
                        Text("${(performanceState.ramUsagePercent * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { performanceState.ramUsagePercent },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = if (performanceState.ramUsagePercent > 0.8f) Color.Red else NabdColors.Rose,
                        trackColor = Color(0xFFEEEEEE)
                    )

                    if (performanceState.ramHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PerformanceChart(
                            dataPoints = performanceState.ramHistory.map { it * 100f },
                            label = "استهلاك RAM %",
                            lineColor = android.graphics.Color.BLUE
                        )
                    }
                }

                // TPS Monitor
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text("سرعة التوليد (Tokens/Sec)", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (performanceState.tpsHistory.isNotEmpty()) {
                        PerformanceChart(
                            dataPoints = performanceState.tpsHistory,
                            label = "t/s",
                            lineColor = android.graphics.Color.MAGENTA
                        )
                    } else {
                        Text(
                            "لا توجد بيانات سرعة حالياً. ابدأ محادثة لقياس الأداء.",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Response Mode Selector
                Text(
                    "نمط الرد",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("fast" to "سريع", "balanced" to "متوازن", "detailed" to "مفصل")
                    modes.forEach { (mode, label) ->
                        val isSelected = responseMode == mode
                        Button(
                            onClick = { modelViewModel.setResponseMode(mode) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) NabdColors.Rose else Color(0xFFEEEEEE),
                                contentColor = if (isSelected) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }

                // Inference Backend Selector
                val inferenceBackend by modelViewModel.inferenceBackend.observeAsState("cpu")
                Text(
                    "محرك التوليد",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val backends = listOf("cpu" to "CPU (XNNPACK)", "gpu" to "GPU", "npu" to "NPU")
                    backends.forEach { (backend, label) ->
                        val isSelected = inferenceBackend == backend
                        Button(
                            onClick = { 
                                modelViewModel.setInferenceBackend(backend)
                                // If already ready, reload with new backend
                                if (modelViewModel.modelState.value is ModelState.Ready) {
                                    modelViewModel.loadModel()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) NabdColors.Rose else Color(0xFFEEEEEE),
                                contentColor = if (isSelected) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(label, fontSize = 10.sp)
                        }
                    }
                }

                // Benchmark Button
                Button(
                    onClick = { modelViewModel.runBenchmark() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !performanceState.isBenchmarking && modelViewModel.isEngineReady(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (performanceState.isBenchmarking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("جاري الفحص...")
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("بدء فحص جهد الهاتف (Benchmark)")
                    }
                }

                performanceState.benchmarkResult?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                Text(
                    "اختيار النموذج المحلي",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = TextStyle(
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Right
                    )
                )

                SUPPORTED_MODELS.forEach { model ->
                    val isSelected = selectedModel?.id == model.id
                    val isImported = modelViewModel.modelManager.isModelImported(model.id)
                    
                    Surface(
                        onClick = {
                            modelViewModel.selectModel(model)
                            if (isImported) modelViewModel.loadModel()
                            showModelSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) NabdColors.Rose.copy(alpha = 0.1f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NabdColors.Rose else Color.Black,
                                    style = TextStyle(
                                        textDirection = TextDirection.Rtl,
                                        textAlign = TextAlign.Right
                                    )
                                )
                                Text(
                                    if (isImported) "جاهز للاستخدام" else "غير مستورد (يتطلب ملف .litertlm)",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    style = TextStyle(
                                        textDirection = TextDirection.Rtl,
                                        textAlign = TextAlign.Right
                                    )
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = NabdColors.Rose
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearMemoryConfirm) {
        ToolConfirmationDialog(
            title = "مسح ذاكرة نبض؟",
            message = "سيتم حذف كل عناصر ذاكرة نبض من هذا الجهاز.",
            onConfirm = {
                memoryViewModel.clearAllMemories()
                showClearMemoryConfirm = false
                chatViewModel.addSystemMessage("تم مسح ذاكرة نبض")
            },
            onCancel = { showClearMemoryConfirm = false }
        )
    }
}
