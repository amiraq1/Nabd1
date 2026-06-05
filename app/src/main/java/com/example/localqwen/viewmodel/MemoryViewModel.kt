package com.example.localqwen.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.localqwen.memory.MemoryExtractor
import com.example.localqwen.memory.MemoryItem
import com.example.localqwen.memory.MemoryPromptBuilder
import com.example.localqwen.memory.MemoryStore
import com.example.localqwen.tools.PhoneToolIntent
import java.util.Locale

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryStore = MemoryStore(application)

    private val _memories = MutableLiveData<List<MemoryItem>>(memoryStore.getAllMemories())
    val memories: LiveData<List<MemoryItem>> = _memories

    private val _isMemoryEnabled = MutableLiveData<Boolean>(memoryStore.isMemoryEnabled())
    val isMemoryEnabled: LiveData<Boolean> = _isMemoryEnabled

    // Phone Tool Confirmation States
    private val _pendingToolIntents = MutableLiveData<List<PhoneToolIntent>?>(null)
    val pendingToolIntents: LiveData<List<PhoneToolIntent>?> = _pendingToolIntents

    private val _originalUserInput = MutableLiveData<String>("")
    val originalUserInput: LiveData<String> = _originalUserInput

    fun refreshMemories() {
        _memories.value = memoryStore.getAllMemories()
    }

    fun toggleMemoryEnabled(enabled: Boolean) {
        memoryStore.setMemoryEnabled(enabled)
        _isMemoryEnabled.value = enabled
    }

    fun addMemory(text: String) {
        memoryStore.addMemory(text, inferMemoryCategory(text))
        refreshMemories()
    }

    fun updateMemory(id: String, newText: String): Boolean {
        val success = memoryStore.updateMemory(id, newText)
        if (success) refreshMemories()
        return success
    }

    fun deleteMemory(id: String) {
        memoryStore.deleteMemory(id)
        refreshMemories()
    }

    fun clearAllMemories() {
        memoryStore.clearAll()
        refreshMemories()
    }

    fun handleMemoryCommand(input: String): MemoryCommandResult? {
        val normalized = input.trim()
        if (normalized.isEmpty()) return null

        when {
            normalized == "ماذا تتذكر عني؟" || normalized == "ماذا تتذكر عني" -> {
                return MemoryCommandResult.ShowList(buildMemoryListText())
            }
            normalized == "امسح ذاكرة نبض" -> {
                return MemoryCommandResult.ConfirmClear
            }
            normalized == "عطّل الذاكرة" || normalized == "عطل الذاكرة" -> {
                toggleMemoryEnabled(false)
                return MemoryCommandResult.Message("تم تعطيل ذاكرة نبض.")
            }
            normalized == "فعّل الذاكرة" || normalized == "فعل الذاكرة" -> {
                toggleMemoryEnabled(true)
                return MemoryCommandResult.Message("تم تفعيل ذاكرة نبض.")
            }
        }

        val extracted = MemoryExtractor.extractMemoryCommand(input) ?: return null

        if (!memoryStore.isMemoryEnabled()) {
            return MemoryCommandResult.Error("ذاكرة نبض معطلة. يمكنك تفعيلها من الإعدادات.")
        }

        validateMemoryInput(extracted)?.let { return MemoryCommandResult.Error(it) }

        memoryStore.addMemory(extracted, inferMemoryCategory(extracted))
        refreshMemories()
        return MemoryCommandResult.Success("تم حفظ ذلك في ذاكرة نبض.")
    }

    fun setPendingTools(userInput: String, intents: List<PhoneToolIntent>) {
        _originalUserInput.value = userInput
        _pendingToolIntents.value = intents
    }

    fun clearPendingTools() {
        _pendingToolIntents.value = null
        _originalUserInput.value = ""
    }

    fun buildMemoryContextForPrompt(): String {
        if (!memoryStore.isMemoryEnabled()) return ""
        return MemoryPromptBuilder.buildMemoryContext(memoryStore.getAllMemories())
    }

    private fun validateMemoryInput(text: String): String? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return "لا يمكن حفظ ذاكرة فارغة."
        if (normalized.length > MAX_MEMORY_ITEM_CHARS) return "المعلومة طويلة جدًا. اكتبها بشكل أقصر."
        if (containsSensitiveMemory(normalized)) return "لا يمكن حفظ معلومات حساسة في ذاكرة نبض."
        return null
    }

    private fun buildMemoryListText(): String {
        val currentMemories = _memories.value ?: emptyList()
        if (currentMemories.isEmpty()) return "لا توجد معلومات محفوظة في ذاكرة نبض."
        return buildString {
            appendLine("هذه المعلومات المحفوظة في ذاكرة نبض:")
            currentMemories.forEach { memory ->
                appendLine("- ${memory.text}")
            }
        }.trim()
    }

    private fun inferMemoryCategory(text: String): String {
        val normalized = text.lowercase(Locale.getDefault())
        return when {
            normalized.contains("أفضل") || normalized.contains("افضل") || normalized.contains("أحب") || normalized.contains("احب") || normalized.contains("لا أحب") || normalized.contains("لا احب") -> {
                MemoryStore.CATEGORY_PREFERENCE
            }
            normalized.contains("اسمي") || normalized.contains("اسم المستخدم") || normalized.contains("عمري") || normalized.contains("مدينتي") || normalized.contains("أعمل") || normalized.contains("اعمل") -> {
                MemoryStore.CATEGORY_PROFILE
            }
            normalized.contains("مشروعي") || normalized.contains("مشروع") || normalized.contains("التطبيق") || normalized.contains("العمل") -> {
                MemoryStore.CATEGORY_PROJECT
            }
            else -> MemoryStore.CATEGORY_GENERAL
        }
    }

    private fun containsSensitiveMemory(text: String): Boolean {
        val normalized = text.lowercase(Locale.getDefault())
        // Check keyword blacklist
        if (SENSITIVE_MEMORY_KEYWORDS.any { keyword -> normalized.contains(keyword) }) {
            return true
        }
        // Check regex patterns for structured sensitive data
        return SENSITIVE_MEMORY_PATTERNS.any { pattern -> pattern.containsMatchIn(normalized) }
    }

    companion object {
        const val MAX_MEMORY_ITEM_CHARS = 300

        private val SENSITIVE_MEMORY_KEYWORDS = listOf(
            // English keywords
            "password", "passwd", "token", "api key", "api_key", "apikey",
            "secret", "secret key", "private key", "access key",
            "credit card", "debit card", "cvv", "cvc", "expiry",
            "ssn", "social security",
            // Arabic keywords — passwords & secrets
            "كلمة المرور", "كلمة السر", "الرقم السري", "رمز الدخول", "رمز التحقق",
            // Arabic keywords — financial
            "بطاقة", "حساب بنكي", "رقم الحساب", "رقم البطاقة", "بطاقة ائتمان",
            "بطاقة ائتمانية", "فيزا", "ماستركارد",
            // Arabic keywords — identity
            "رقم الهوية", "رقم الجواز", "جواز السفر", "رقم الإقامة"
        )

        private val SENSITIVE_MEMORY_PATTERNS = listOf(
            // Credit card numbers: 13-19 digits, optionally separated by spaces or dashes
            Regex("\\b(?:\\d[ -]?){13,19}\\b"),
            // National ID / Iqama: exactly 10 digits
            Regex("\\b\\d{10}\\b"),
            // IBAN: 2 letters + 2 digits + up to 30 alphanumeric
            Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4,30}\\b", RegexOption.IGNORE_CASE),
            // Email addresses
            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            // Phone numbers with country code (e.g., +966XXXXXXXXX, +1XXXXXXXXXX)
            Regex("\\+\\d{1,3}[\\s-]?\\d{7,14}")
        )
    }
}

sealed class MemoryCommandResult {
    data class ShowList(val text: String) : MemoryCommandResult()
    data object ConfirmClear : MemoryCommandResult()
    data class Success(val message: String) : MemoryCommandResult()
    data class Message(val message: String) : MemoryCommandResult()
    data class Error(val message: String) : MemoryCommandResult()
}
