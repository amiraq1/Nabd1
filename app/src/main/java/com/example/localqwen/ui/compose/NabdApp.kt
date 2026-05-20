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
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.Role
import com.example.localqwen.viewmodel.ChatViewModel
import com.example.localqwen.viewmodel.ModelViewModel
import com.example.localqwen.viewmodel.MemoryViewModel
import com.example.localqwen.viewmodel.StatusEvent
import com.example.localqwen.viewmodel.MemoryCommandResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NabdApp(
    chatViewModel: ChatViewModel,
    modelViewModel: ModelViewModel,
    memoryViewModel: MemoryViewModel,
    onOpenSettings: () -> Unit
) {
    val messages by chatViewModel.messages.observeAsState(emptyList())
    val isGenerating by chatViewModel.isGenerating.observeAsState(false)
    val isProcessingDocument by chatViewModel.isProcessingDocument.observeAsState(false)
    val selectedModel by modelViewModel.selectedModel.observeAsState()
    val modelState by modelViewModel.modelState.observeAsState()
    val statusEvent by chatViewModel.statusEvent.observeAsState()
    val modelStatusEvent by modelViewModel.statusEvent.observeAsState()
    val currentTps by chatViewModel.currentTps.observeAsState(0f)
    val performanceState by modelViewModel.performanceState.observeAsState(com.example.localqwen.viewmodel.PerformanceState())
    
    var statusText by remember { mutableStateOf("جاهز") }
    var showClearMemoryConfirm by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(modelState) {
        modelState?.let { state ->
            statusText = when (state) {
                is com.example.localqwen.viewmodel.ModelState.Loading -> "جاري تشغيل نبض..."
                is com.example.localqwen.viewmodel.ModelState.Ready -> "جاهز"
                is com.example.localqwen.viewmodel.ModelState.Idle -> "غير مشغّل"
                is com.example.localqwen.viewmodel.ModelState.NotImported -> "غير مستورد"
                is com.example.localqwen.viewmodel.ModelState.Error -> state.message
            }
        }
    }

    LaunchedEffect(currentTps) {
        if (currentTps > 0) {
            modelViewModel.addTpsRecord(currentTps)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { chatViewModel.importDocument(it) }
    }

    LaunchedEffect(statusEvent, modelStatusEvent, currentTps) {
        (statusEvent ?: modelStatusEvent)?.let { event ->
            val base = when (event) {
                is StatusEvent.Info -> event.message
                is StatusEvent.Success -> event.message
                is StatusEvent.Error -> event.message
            }
            statusText = if (currentTps > 0) "$base (${"%.1f".format(currentTps)} t/s)" else base
        }
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
            onSendMessage = handleSendMessage,
            onAddAttachment = { documentPickerLauncher.launch("*/*") },
            onShowHistory = { showMemoryDialog = true },
            onShowMenu = onOpenSettings,
            onModelBadgeClick = { showModelSheet = true }
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
            isGenerating = isGenerating || isProcessingDocument,
            onSendMessage = handleSendMessage,
            onAddAttachment = { documentPickerLauncher.launch("*/*") },
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
                        color = if (performanceState.ramUsagePercent > 0.8f) Color.Red else Color(0xFFFF5A5F),
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
                                containerColor = if (isSelected) Color(0xFFFF5A5F) else Color(0xFFEEEEEE),
                                contentColor = if (isSelected) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(label, fontSize = 12.sp)
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

                ModelManager.SUPPORTED_MODELS.forEach { model ->
                    val isSelected = selectedModel?.id == model.id
                    val isImported = modelViewModel.modelManager.isModelImported(model.id)
                    
                    Surface(
                        onClick = {
                            modelViewModel.selectModel(model)
                            if (isImported) modelViewModel.loadModel()
                            showModelSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) Color(0xFFFF5A5F).copy(alpha = 0.1f) else Color.Transparent,
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
                                    color = if (isSelected) Color(0xFFFF5A5F) else Color.Black,
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
                                    tint = Color(0xFFFF5A5F)
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
