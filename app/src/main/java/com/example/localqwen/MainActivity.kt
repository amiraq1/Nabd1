package com.example.localqwen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.Editable
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.Role
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.SupportedModel
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var typingIndicatorView: TextView
    private lateinit var inputView: EditText
    private lateinit var attachButton: Button
    private lateinit var optionsButton: Button
    private lateinit var sendButton: Button
    private lateinit var sendProgressBar: ProgressBar
    private lateinit var modelManager: ModelManager

    private val pickModelRequestCode = 200
    private val pickImageRequestCode = 201
    private val pickPdfRequestCode = 202
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val chatMessages = mutableListOf<ChatMessage>()
    private val supportedModels = ModelManager.SUPPORTED_MODELS
    private val preferences by lazy { getSharedPreferences("nabd_prefs", MODE_PRIVATE) }
    private var selectedModel: SupportedModel = supportedModels.first()
    private var loadedModelId: String? = null
    private var lastAssistantResponse: String = ""
    private var activeAssistantMessageIndex: Int = -1

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    private var isLoadingModel = false
    private var isGenerating = false
    private var isProcessingFile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modelManager = ModelManager(this)

        statusView = findViewById(R.id.statusTextView)
        chatContainer = findViewById(R.id.chatContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        typingIndicatorView = findViewById(R.id.tvTypingIndicator)
        inputView = findViewById(R.id.etMessage)
        attachButton = findViewById(R.id.btnAttachFile)
        optionsButton = findViewById(R.id.optionsButton)
        sendButton = findViewById(R.id.btnSend)
        sendProgressBar = findViewById(R.id.sendProgressBar)
        selectedModel = supportedModels.find { it.id == preferences.getString(KEY_SELECTED_MODEL_ID, null) }
            ?: supportedModels.first()

        setStatusInfo(currentStatus())
        renderChatHistory()

        optionsButton.setOnClickListener { showOptionsBottomSheet() }
        attachButton.setOnClickListener { showAttachmentTypeDialog() }
        sendButton.setOnClickListener { sendPrompt() }
        inputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateButtons()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        restoreChatHistory()
        updateButtons()
    }

    override fun onDestroy() {
        closeModelResources()
        scope.cancel()
        super.onDestroy()
    }

    private fun currentStatus(): String {
        val baseStatus = if (loadedModelId == selectedModel.id && engine != null && conversation != null) {
            "جاهز • ${selectedModel.displayName}"
        } else if (modelManager.isModelReady(selectedModel)) {
            "غير مشغّل • ${selectedModel.displayName}"
        } else {
            "غير مستورد • ${selectedModel.displayName}"
        }
        return if (selectedModel.id == "gemma_e4b") {
            "$baseStatus\nتنبيه: Gemma E4B أثقل وقد يحتاج وقتًا أطول وذاكرة أكبر."
        } else {
            baseStatus
        }
    }

    private fun updateButtons() {
        val hasInput = inputView.text?.toString()?.trim()?.isNotEmpty() == true
        val busy = isLoadingModel || isGenerating || isProcessingFile

        optionsButton.isEnabled = !busy
        attachButton.isEnabled = !busy
        sendButton.isEnabled = !busy && hasInput
        inputView.isEnabled = !busy
        sendButton.text = if (isGenerating) "…" else "↑"
        sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.5f
        attachButton.text = "+"
        attachButton.alpha = if (attachButton.isEnabled) 1.0f else 0.5f
        typingIndicatorView.visibility = if (isGenerating) View.VISIBLE else View.GONE
        sendProgressBar.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun setStatusInfo(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_text_secondary))
    }

    private fun setStatusSuccess(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_success))
    }

    private fun setStatusError(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_error))
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, pickModelRequestCode)
    }

    private fun openImagePicker() {
        if (conversation == null || loadedModelId != selectedModel.id) {
            setStatusError("يرجى تشغيل نبض أولًا قبل تحليل الملفات")
            updateButtons()
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, pickImageRequestCode)
    }

    private fun openPdfPicker() {
        if (conversation == null || loadedModelId != selectedModel.id) {
            setStatusError("يرجى تشغيل نبض أولًا قبل تحليل الملفات")
            updateButtons()
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, pickPdfRequestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == pickModelRequestCode && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                setStatusError("No file selected.")
                return
            }
            importModelFromUri(uri)
            return
        }

        if (requestCode == pickImageRequestCode && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                setStatusError("لم يتم اختيار صورة")
                return
            }
            processImageUri(uri)
            return
        }

        if (requestCode == pickPdfRequestCode && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                setStatusError("لم يتم اختيار ملف PDF")
                return
            }
            processPdfUri(uri)
        }
    }

    private fun importModelFromUri(uri: Uri) {
        scope.launch {
            try {
                isLoadingModel = true
                updateButtons()
                setStatusInfo("Importing model...")

                withContext(Dispatchers.IO) {
                    closeModelResources()
                    modelManager.modelFile(selectedModel).parentFile?.mkdirs()

                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Cannot open selected file." }
                        modelManager.modelFile(selectedModel).outputStream().use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }

                setStatusSuccess(currentStatus())
            } catch (e: Exception) {
                setStatusError("Import failed:\n${e.javaClass.simpleName}\n${e.message}")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun loadModel(onLoaded: (() -> Unit)? = null) {
        if (!modelManager.isModelReady(selectedModel)) {
            setStatusError("Model missing. Import a LiteRT-LM model first.")
            updateButtons()
            return
        }

        if (engine != null && conversation != null && loadedModelId == selectedModel.id) {
            setStatusSuccess("تم تشغيل نبض باستخدام ${selectedModel.displayName}")
            updateButtons()
            onLoaded?.invoke()
            return
        }

        isLoadingModel = true
        updateButtons()
        setStatusInfo("Loading model...\nThis can take several seconds.")

        scope.launch {
            try {
                val loadedPair = withContext(Dispatchers.IO) {
                    closeModelResources()
                    Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

                    val loadedEngine = Engine(
                        EngineConfig(
                            modelPath = modelManager.modelPath(selectedModel),
                            cacheDir = cacheDir.absolutePath
                        )
                    )
                    loadedEngine.initialize()
                    val loadedConversation = loadedEngine.createConversation()
                    loadedEngine to loadedConversation
                }

                engine = loadedPair.first
                conversation = loadedPair.second
                loadedModelId = selectedModel.id
                setStatusSuccess("تم تشغيل نبض باستخدام ${selectedModel.displayName}")
                onLoaded?.invoke()
            } catch (e: Exception) {
                closeModelResources()
                setStatusError("Model load failed:\n${e.javaClass.simpleName}\n${e.message}")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun unloadModel() {
        scope.launch {
            isLoadingModel = true
            updateButtons()
            setStatusInfo("Unloading model...")

            try {
                withContext(Dispatchers.IO) {
                    closeModelResources()
                }
                setStatusInfo(
                    if (modelManager.isModelReady(selectedModel)) {
                        "تم إيقاف نبض.\n${currentStatus()}"
                    } else {
                        currentStatus()
                    }
                )
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun closeModelResources() {
        try {
            conversation?.close()
        } catch (_: Exception) {
        } finally {
            conversation = null
        }

        try {
            engine?.close()
        } catch (_: Exception) {
        } finally {
            engine = null
            loadedModelId = null
        }
    }

    private fun clearChat() {
        chatMessages.clear()
        activeAssistantMessageIndex = -1
        lastAssistantResponse = ""
        clearSavedChatHistory()
        renderChatHistory()
        setStatusInfo(currentStatus())
        updateButtons()
    }

    private fun processImageUri(uri: Uri) {
        if (conversation == null || loadedModelId != selectedModel.id) {
            setStatusError("يرجى تشغيل نبض أولًا قبل تحليل الملفات")
            updateButtons()
            return
        }

        isProcessingFile = true
        updateButtons()
        setStatusInfo("جارٍ استخراج النص من الصورة...")

        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            isProcessingFile = false
            updateButtons()
            setStatusError("تعذر فتح الصورة:\n${e.javaClass.simpleName}\n${e.message}")
            return
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val extractedText = result.text.trim()
                if (extractedText.isEmpty()) {
                    isProcessingFile = false
                    updateButtons()
                    setStatusError("لم يتم العثور على نص واضح في الصورة")
                    return@addOnSuccessListener
                }

                addChatMessage(
                    ChatMessage(
                        Role.USER,
                        "أرسلت صورة للتحليل.\nتم استخراج نص من الصورة وإرساله إلى نبض للتحليل."
                    )
                )
                val assistantEntry = ChatMessage(Role.ASSISTANT, "")
                addChatMessage(assistantEntry)
                saveChatHistory()

                val prompt = """
                    أنت "نبض"، مساعد ذكاء اصطناعي محلي بإعداد وتطوير عمار محمد التميمي.
                    حلل النص المستخرج من الصورة.
                    أجب بالعربية فقط.
                    لا تستخدم Markdown.
                    اكتب شرحًا واضحًا ومفيدًا.
                    
                    النص المستخرج:
                    $extractedText
                """.trimIndent()

                isProcessingFile = false
                startAssistantGeneration(prompt, assistantEntry, "جارٍ تحليل النص المستخرج...")
            }
            .addOnFailureListener { error ->
                isProcessingFile = false
                updateButtons()
                setStatusError("فشل استخراج النص من الصورة:\n${error.javaClass.simpleName}\n${error.message}")
            }
            .addOnCompleteListener {
                recognizer.close()
            }
    }

    private fun processPdfUri(uri: Uri) {
        if (conversation == null || loadedModelId != selectedModel.id) {
            setStatusError("يرجى تشغيل نبض أولًا قبل تحليل الملفات")
            updateButtons()
            return
        }

        isProcessingFile = true
        updateButtons()
        setStatusInfo("جاري قراءة ملف PDF...")

        scope.launch {
            var fileDescriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null

            try {
                fileDescriptor = withContext(Dispatchers.IO) {
                    contentResolver.openFileDescriptor(uri, "r")
                } ?: error("تعذر فتح ملف PDF")

                renderer = PdfRenderer(fileDescriptor)
                val pageCount = minOf(renderer.pageCount, 3)
                val processedText = StringBuilder()

                for (pageIndex in 0 until pageCount) {
                    setStatusInfo("جاري استخراج النص من الصفحة ${pageIndex + 1}...")
                    val bitmap = withContext(Dispatchers.IO) {
                        renderer.openPage(pageIndex).use { page ->
                            val scale = 1.5f
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(Color.WHITE)
                                page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }

                    try {
                        val pageText = recognizeTextFromImage(InputImage.fromBitmap(bitmap, 0)).trim()
                        if (pageText.isNotEmpty()) {
                            if (processedText.isNotEmpty()) processedText.append("\n\n")
                            processedText.append("الصفحة ${pageIndex + 1}:\n")
                            processedText.append(pageText)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                val extractedText = processedText.toString().trim()
                if (extractedText.isEmpty()) {
                    setStatusError("لم يتم العثور على نص واضح في ملف PDF")
                    return@launch
                }

                val wasTruncated = extractedText.length > 6000
                val promptText = if (wasTruncated) extractedText.take(6000) else extractedText
                addChatMessage(ChatMessage(Role.USER, buildPdfSummary(renderer.pageCount, pageCount, wasTruncated)))
                val assistantEntry = ChatMessage(Role.ASSISTANT, "")
                addChatMessage(assistantEntry)
                saveChatHistory()

                val prompt = """
                    أنت "نبض"، مساعد ذكاء اصطناعي محلي بإعداد وتطوير عمار محمد التميمي.
                    حلل النص المستخرج من ملف PDF.
                    أجب بالعربية فقط.
                    لا تستخدم Markdown.
                    لا تستخدم النجوم **.
                    لا تعرض التفكير الداخلي.
                    لا تستخدم رموزًا خاصة.
                    اكتب ملخصًا واضحًا، ثم أهم النقاط، ثم إن وجدت ملاحظات أو استنتاجات.
                    ${if (wasTruncated) "تم اختصار النص بسبب الطول." else ""}
                    
                    النص المستخرج من PDF:
                    $promptText
                """.trimIndent()

                isProcessingFile = false
                startAssistantGeneration(
                    prompt = prompt,
                    assistantMessage = assistantEntry,
                    statusMessage = "جاري تحليل النص بواسطة نبض...",
                    completionStatus = "اكتمل تحليل ملف PDF"
                )
            } catch (e: Exception) {
                setStatusError("تعذر تحليل ملف PDF: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                renderer?.close()
                fileDescriptor?.close()
                if (isProcessingFile) {
                    isProcessingFile = false
                    updateButtons()
                }
            }
        }
    }

    private fun sendPrompt() {
        if (isLoadingModel || isGenerating || isProcessingFile) return

        val userInput = inputView.text.toString().trim()
        if (userInput.isEmpty()) {
            setStatusError("اكتب رسالة أولًا")
            return
        }

        if (!modelManager.isModelReady(selectedModel)) {
            setStatusError("يرجى تشغيل نبض أولًا")
            updateButtons()
            return
        }

        if (conversation == null || loadedModelId != selectedModel.id) {
            setStatusError("يرجى تشغيل نبض أولًا")
            return
        }

        val prompt = """
            أنت "نبض"، مساعد ذكاء اصطناعي محلي بإعداد وتطوير عمار محمد التميمي.
            أجب بالعربية فقط.
            أجب مباشرة على سؤال المستخدم.
            لا تطلب تفاصيل إضافية إلا إذا كان السؤال غامضًا جدًا.
            لا تستخدم Markdown.
            لا تستخدم النجوم **.
            لا تعرض التفكير الداخلي.
            لا تستخدم رموزًا خاصة.
            اكتب إجابة واضحة ومختصرة من 2 إلى 5 جمل.
            ${if (selectedModel.id == "gemma_e4b") "إذا كان السؤال تحليليًا أو معقدًا، يمكنك التفصيل من 5 إلى 10 جمل." else ""}
            
            رسالة المستخدم:
            $userInput
        """.trimIndent()

        inputView.setText("")
        addChatMessage(ChatMessage(Role.USER, userInput))
        val assistantEntry = ChatMessage(Role.ASSISTANT, "")
        addChatMessage(assistantEntry)
        saveChatHistory()

        startAssistantGeneration(prompt, assistantEntry, "Generating response...")
    }

    private fun addChatMessage(message: ChatMessage) {
        chatMessages.add(message)
        if (message.role == Role.ASSISTANT) {
            activeAssistantMessageIndex = chatMessages.lastIndex
        }
        renderChatHistory()
    }

    private fun updateAssistantMessage(text: String) {
        if (activeAssistantMessageIndex !in chatMessages.indices) return
        chatMessages[activeAssistantMessageIndex].text = text
        renderChatHistory()
    }

    private fun renderChatHistory() {
        chatContainer.removeAllViews()

        if (chatMessages.isEmpty()) {
            chatContainer.addView(createEmptyStateView())
        } else {
            chatMessages
                .filter { it.text.isNotBlank() }
                .forEach { message ->
                    val messageView = when (message.role) {
                        Role.USER -> createUserMessageView(message.text)
                        Role.ASSISTANT -> createAssistantMessageView(message.text)
                        Role.SYSTEM -> createSystemMessageView(message.text)
                    }
                    chatContainer.addView(messageView)
                }
        }

        scrollMessagesToBottom()
    }

    private fun scrollMessagesToBottom() {
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun createEmptyStateView(): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(48)
            }
            gravity = Gravity.CENTER
            text = "ابدأ محادثة جديدة مع نبض\n\nاكتب رسالة، أو أضف صورة أو ملف PDF للتحليل."
            setTextColor(ContextCompat.getColor(context, R.color.nabd_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(dpToPx(6).toFloat(), 1f)
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
    }

    private fun createUserMessageView(message: String): View {
        val wrapper = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14)
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            textDirection = View.TEXT_DIRECTION_LOCALE
        }

        val labelView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(4)
                bottomMargin = dpToPx(4)
            }
            text = "أنت"
            setTextColor(ContextCompat.getColor(context, R.color.nabd_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            textDirection = View.TEXT_DIRECTION_LOCALE
        }

        val messageView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
            setBackgroundResource(R.drawable.bg_user_message_inline)
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setTextColor(ContextCompat.getColor(context, R.color.nabd_on_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(dpToPx(6).toFloat(), 1f)
            textDirection = View.TEXT_DIRECTION_LOCALE
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextIsSelectable(true)
            setText(message)
            setOnLongClickListener {
                copyMessageText(message)
                true
            }
        }

        wrapper.addView(labelView)
        wrapper.addView(messageView)
        return wrapper
    }

    private fun createAssistantMessageView(message: String): View {
        val messageText = buildAssistantDisplayText(message)
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14)
            }
            setTextColor(ContextCompat.getColor(context, R.color.nabd_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(dpToPx(6).toFloat(), 1f)
            textDirection = View.TEXT_DIRECTION_LOCALE
            gravity = Gravity.START
            setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(10))
            setTextIsSelectable(true)
            setText(messageText)
            setOnLongClickListener {
                copyMessageText(message)
                true
            }
        }
    }

    private fun createSystemMessageView(text: String): View {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.nabd_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dpToPx(4).toFloat(), 1f)
            textDirection = View.TEXT_DIRECTION_LOCALE
            this.text = text
        }
    }

    private fun buildAssistantDisplayText(text: String): SpannableStringBuilder {
        val label = "نبض:\n"
        val builder = SpannableStringBuilder(label + text)
        builder.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.nabd_accent)),
            0,
            label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    private fun copyMessageText(text: String) {
        if (text.isBlank()) return
        copyToClipboard("رسالة", text)
        Toast.makeText(this, "تم نسخ الرسالة", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun roleLabel(role: Role): String {
        return when (role) {
            Role.USER -> "أنت"
            Role.ASSISTANT -> "نبض"
            Role.SYSTEM -> "النظام"
        }
    }

    private fun copyFullConversation() {
        val conversationText = buildChatHistoryText().trim()
        if (conversationText.isBlank() || chatMessages.isEmpty()) {
            setStatusError("لا توجد محادثة لنسخها")
            Toast.makeText(this, "لا توجد محادثة لنسخها", Toast.LENGTH_SHORT).show()
            return
        }

        copyToClipboard("محادثة نبض", conversationText)
        setStatusSuccess("تم نسخ المحادثة")
        Toast.makeText(this, "تم نسخ المحادثة", Toast.LENGTH_SHORT).show()
    }

    private fun copyLastAssistantResponse() {
        if (lastAssistantResponse.isBlank()) {
            setStatusError("لا يوجد رد لنسخه")
            Toast.makeText(this, "لا يوجد رد لنسخه", Toast.LENGTH_SHORT).show()
            return
        }

        copyToClipboard("آخر رد من نبض", lastAssistantResponse)
        setStatusSuccess("تم نسخ آخر رد")
        Toast.makeText(this, "تم نسخ آخر رد", Toast.LENGTH_SHORT).show()
    }

    private fun pasteFromClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        val pastedText = clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.takeIf { it.isNotBlank() }

        if (pastedText.isNullOrEmpty()) {
            setStatusError("الحافظة فارغة")
            Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
            return
        }

        val editable = inputView.editableText
        val cursorPosition = inputView.selectionStart.coerceAtLeast(0).coerceAtMost(editable.length)
        editable.insert(cursorPosition, pastedText)
        inputView.setSelection((cursorPosition + pastedText.length).coerceAtMost(editable.length))
        inputView.requestFocus()
        setStatusSuccess("تم اللصق من الحافظة")
        Toast.makeText(this, "تم اللصق من الحافظة", Toast.LENGTH_SHORT).show()
        updateButtons()
    }

    private fun cleanModelOutput(text: String): String {
        return text
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace("<think>", "")
            .replace("</think>", "")
            .replace("<\\|im_start\\|>", "")
            .replace("<\\|im_end\\|>", "")
            .replace("<\\|endoftext\\|>", "")
            .replace("<\\|assistant\\|>", "")
            .replace("<\\|user\\|>", "")
            .replace("Ġ", " ")
            .replace("Â", "")
            .replace("�", "")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun cleanForDisplay(text: String): String {
        return cleanModelOutput(text)
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace("**", "")
            .replace(Regex("```[a-zA-Z0-9_+-]*\\n?"), "")
            .replace("```", "")
            .replace(Regex("(?m)^[ \\t]*[*-][ \\t]+"), "• ")
            .trim()
    }

    private fun buildChatHistoryText(): String {
        return chatMessages
            .filter { it.text.isNotBlank() }
            .joinToString("\n\n") { message ->
                when (message.role) {
                    Role.USER -> "أنت:\n${message.text}"
                    Role.ASSISTANT -> "نبض:\n${message.text}"
                    Role.SYSTEM -> message.text
                }
            }
    }

    private suspend fun recognizeTextFromImage(image: InputImage): String =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                .addOnCompleteListener {
                    recognizer.close()
                }
        }

    private fun buildPdfSummary(totalPages: Int, processedPages: Int, wasTruncated: Boolean): String {
        return buildString {
            append("أرسلت ملف PDF للتحليل.")
            append("\nتم استخراج نص من الملف وإرساله إلى نبض للتحليل.")
            if (totalPages > processedPages) {
                append("\nتم تحليل أول 3 صفحات فقط لتقليل استهلاك الذاكرة.")
            }
            if (wasTruncated) {
                append("\nتم اختصار النص بسبب الطول.")
            }
        }
    }

    private fun startAssistantGeneration(
        prompt: String,
        assistantMessage: ChatMessage,
        statusMessage: String,
        completionStatus: String = "Ready."
    ) {
        isGenerating = true
        updateButtons()
        setStatusInfo(statusMessage)

        scope.launch {
            try {
                val activeConversation = conversation ?: error("Model is not loaded.")
                val streamedOutput = StringBuilder()

                withContext(Dispatchers.IO) {
                    activeConversation.sendMessageAsync(prompt).collect { chunk ->
                        streamedOutput.append(chunk.toString())
                        val cleanedPartial = cleanForDisplay(streamedOutput.toString())
                        withContext(Dispatchers.Main) {
                            assistantMessage.text = cleanedPartial
                            updateAssistantMessage(cleanedPartial)
                        }
                    }
                }

                val finalText = cleanForDisplay(streamedOutput.toString()).ifBlank { "(Empty response)" }
                assistantMessage.text = finalText
                lastAssistantResponse = finalText
                updateAssistantMessage(finalText)
                saveChatHistory()
                setStatusSuccess(completionStatus)
            } catch (e: Exception) {
                val errorText = "تعذر إنشاء الرد."
                assistantMessage.text = errorText
                lastAssistantResponse = errorText
                updateAssistantMessage(errorText)
                saveChatHistory()
                setStatusError("Generation failed:\n${e.javaClass.simpleName}\n${e.message}")
            } finally {
                isGenerating = false
                updateButtons()
            }
        }
    }

    private fun saveSelectedModel() {
        preferences.edit()
            .putString(KEY_SELECTED_MODEL_ID, selectedModel.id)
            .apply()
    }

    private fun selectModel(position: Int) {
        val newModel = supportedModels[position]
        val previousModelId = selectedModel.id
        selectedModel = newModel
        saveSelectedModel()
        if (loadedModelId != null && loadedModelId != newModel.id) {
            unloadModel()
        } else if (previousModelId != newModel.id) {
            setStatusInfo(currentStatus())
            updateButtons()
        }
    }

    private fun saveChatHistory() {
        val historyJson = JSONArray().apply {
            chatMessages.forEach { message ->
                put(
                    JSONObject()
                        .put("role", message.role.name)
                        .put("text", message.text)
                        .put("timestamp", message.timestamp)
                )
            }
        }

        preferences.edit()
            .putString(KEY_CHAT_MESSAGES_JSON, historyJson.toString())
            .putString(KEY_CHAT_HISTORY_TEXT, buildChatHistoryText())
            .putString(KEY_SELECTED_MODEL_ID, selectedModel.id)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    private fun restoreChatHistory() {
        chatMessages.clear()

        val structuredHistory = preferences.getString(KEY_CHAT_MESSAGES_JSON, null)
        if (!structuredHistory.isNullOrBlank()) {
            runCatching {
                val historyJson = JSONArray(structuredHistory)
                for (index in 0 until historyJson.length()) {
                    val item = historyJson.getJSONObject(index)
                    chatMessages.add(
                        ChatMessage(
                            role = runCatching { Role.valueOf(item.optString("role", Role.ASSISTANT.name)) }
                                .getOrDefault(Role.ASSISTANT),
                            text = item.optString("text", ""),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } else {
            val textHistory = preferences.getString(KEY_CHAT_HISTORY_TEXT, null)
            if (!textHistory.isNullOrBlank()) {
                chatMessages.add(ChatMessage(Role.SYSTEM, textHistory))
            }
            val legacyHistory = preferences.getString(KEY_CHAT_HISTORY, null)
            if (chatMessages.isEmpty() && !legacyHistory.isNullOrBlank()) {
                val parsedLegacy = runCatching {
                    val historyJson = JSONArray(legacyHistory)
                    for (index in 0 until historyJson.length()) {
                        val item = historyJson.getJSONObject(index)
                        val label = item.optString("label", "نبض")
                        val role = when (label) {
                            "أنت" -> Role.USER
                            "نبض" -> Role.ASSISTANT
                            else -> Role.SYSTEM
                        }
                        chatMessages.add(
                            ChatMessage(
                                role = role,
                                text = item.optString("text", "")
                            )
                        )
                    }
                }
                if (parsedLegacy.isFailure) {
                    chatMessages.add(ChatMessage(Role.SYSTEM, legacyHistory))
                }
            }
        }

        activeAssistantMessageIndex = chatMessages.indexOfLast { it.role == Role.ASSISTANT }
        lastAssistantResponse = chatMessages.lastOrNull { it.role == Role.ASSISTANT }?.text?.trim().orEmpty()
        renderChatHistory()
    }

    private fun clearSavedChatHistory() {
        preferences.edit()
            .remove(KEY_CHAT_MESSAGES_JSON)
            .remove(KEY_CHAT_HISTORY_TEXT)
            .remove(KEY_CHAT_HISTORY)
            .remove(KEY_LAST_UPDATED)
            .apply()
    }

    private fun showAboutDialog() {
        val message = """
            نبض
            مساعد ذكاء اصطناعي محلي يعمل على جهازك بدون خادم.

            بإعداد وتطوير عمار محمد التميمي.
            
            يعتمد على LiteRT-LM لتشغيل النماذج محليًا.
            
            النماذج المدعومة:
            • Gemma E2B للمهام اليومية
            • Gemma E4B للمهام الثقيلة
            
            الخصوصية:
            تتم المعالجة داخل الجهاز، ولا يتم إرسال المحادثات إلى خادم خارجي من داخل التطبيق.
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("حول نبض")
            .setMessage(message)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun modelDescription(model: SupportedModel): String {
        return when (model.id) {
            "gemma_e2b" -> "${model.displayName} — سريع ومتوازن"
            "gemma_e4b" -> "${model.displayName} — للمهام الثقيلة"
            else -> model.displayName
        }
    }

    private fun currentModelStatusLabel(): String {
        return if (engine != null && conversation != null && loadedModelId == selectedModel.id) {
            "مشغّل"
        } else if (modelManager.isModelReady(selectedModel)) {
            "جاهز"
        } else {
            "غير مستورد"
        }
    }

    private fun showOptionsBottomSheet() {
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_options, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetView)
        dialog.setCanceledOnTouchOutside(true)

        val subtitleView = sheetView.findViewById<TextView>(R.id.tvOptionsSubtitle)
        val statusChip = sheetView.findViewById<TextView>(R.id.tvStatusChip)
        val sectionModel = sheetView.findViewById<LinearLayout>(R.id.sectionModel)
        val sectionTools = sheetView.findViewById<LinearLayout>(R.id.sectionTools)
        val sectionConversation = sheetView.findViewById<LinearLayout>(R.id.sectionConversation)
        val sectionInfo = sheetView.findViewById<LinearLayout>(R.id.sectionInfo)

        subtitleView.text = modelDescription(selectedModel)

        val statusLabel = currentModelStatusLabel()
        statusChip.text = statusLabel
        if (statusLabel == "غير مستورد") {
            statusChip.background = ContextCompat.getDrawable(this, R.drawable.bg_status_chip_inactive)
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.nabd_text_secondary))
        } else {
            statusChip.background = ContextCompat.getDrawable(this, R.drawable.bg_status_chip_ready)
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.nabd_success))
        }

        val toggleLabel = if (engine != null && conversation != null && loadedModelId == selectedModel.id) {
            "إيقاف نبض"
        } else {
            "تشغيل نبض"
        }

        addOptionRow(sectionModel, "◉", "اختيار النموذج", modelDescription(selectedModel)) {
            dialog.dismiss()
            showModelSelectionDialog()
        }
        addOptionRow(sectionModel, "⇩", "استيراد النموذج") {
            dialog.dismiss()
            openFilePicker()
        }
        addOptionRow(sectionModel, if (toggleLabel == "إيقاف نبض") "■" else "▶", toggleLabel) {
            dialog.dismiss()
            if (engine != null && conversation != null && loadedModelId == selectedModel.id) unloadModel() else loadModel()
        }

        addOptionRow(sectionTools, "＋", "إضافة ملف") {
            dialog.dismiss()
            showAttachmentTypeDialog()
        }
        addOptionRow(sectionTools, "⧉", "لصق من الحافظة") {
            dialog.dismiss()
            pasteFromClipboard()
        }

        addOptionRow(sectionConversation, "⧉", "نسخ المحادثة") {
            dialog.dismiss()
            copyFullConversation()
        }
        addOptionRow(sectionConversation, "⧉", "نسخ آخر رد") {
            dialog.dismiss()
            copyLastAssistantResponse()
        }
        addOptionRow(
            container = sectionConversation,
            icon = "×",
            title = "مسح المحادثة",
            titleColor = ContextCompat.getColor(this, R.color.nabd_error)
        ) {
            dialog.dismiss()
            confirmClearChat()
        }

        addOptionRow(sectionInfo, "؟", "حول نبض") {
            dialog.dismiss()
            showAboutDialog()
        }

        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return@setOnShowListener
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val screenHeight = resources.displayMetrics.heightPixels
            val peekHeight = (screenHeight * 0.60f).toInt()
            val maxHeight = (screenHeight * 0.75f).toInt()

            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = maxHeight
            }
            behavior.peekHeight = peekHeight
            behavior.skipCollapsed = false
            behavior.isDraggable = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        dialog.show()
    }

    private fun addOptionRow(
        container: LinearLayout,
        icon: String,
        title: String,
        subtitle: String? = null,
        titleColor: Int = ContextCompat.getColor(this, R.color.nabd_on_surface),
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_option_row, container, false)
        val iconView = row.findViewById<TextView>(R.id.tvOptionIcon)
        val titleView = row.findViewById<TextView>(R.id.tvOptionTitle)
        val subtitleView = row.findViewById<TextView>(R.id.tvOptionSubtitle)

        iconView.text = icon
        titleView.text = title
        titleView.setTextColor(titleColor)

        if (subtitle.isNullOrBlank()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.text = subtitle
            subtitleView.visibility = View.VISIBLE
        }

        row.setOnClickListener { onClick() }
        container.addView(row)
    }

    private fun confirmClearChat() {
        MaterialAlertDialogBuilder(this)
            .setTitle("مسح المحادثة؟")
            .setMessage("سيتم حذف المحادثة الحالية من الشاشة والحفظ المحلي.")
            .setPositiveButton("مسح") { _, _ -> clearChat() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showAttachmentTypeDialog() {
        val types = arrayOf("صورة", "ملف PDF")
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر نوع الملف")
            .setItems(types) { _, which ->
                when (which) {
                    0 -> openImagePicker()
                    1 -> openPdfPicker()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showModelSelectionDialog() {
        val selectedIndex = supportedModels.indexOfFirst { it.id == selectedModel.id }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("اختيار النموذج")
            .setSingleChoiceItems(
                supportedModels.map { modelDescription(it) }.toTypedArray(),
                selectedIndex
            ) { dialog, which ->
                selectModel(which)
                dialog.dismiss()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    companion object {
        private const val KEY_CHAT_HISTORY = "chat_history"
        private const val KEY_CHAT_HISTORY_TEXT = "chat_history_text"
        private const val KEY_CHAT_MESSAGES_JSON = "chat_messages_json"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_LAST_UPDATED = "last_updated"
    }
}
