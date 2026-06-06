package com.example.localqwen.utils

import com.example.localqwen.chat.ChatMessage
import android.util.Log

object ContextEngineer {
    private const val TAG = "ContextEngineer"
    private const val MAX_ALLOWED_TOKENS = 1024
    
    // تقدير تقريبي: كل كلمة عربية/إنجليزية مع المسافات وعلامات الترقيم تساوي حوالي 1.5 إلى 2 توكنز في Qwen/Gemma
    private fun estimateTokens(text: String): Int {
        // Estimation logic: Arabic text is denser in tokens. Using 2.2 tokens per 3.5 characters for safety.
        return (text.length / 3.5 * 2.2).toInt().coerceAtLeast(2)
    }

    /**
     * هندسة السياق: تأخذ تاريخ المحادثة الكامل والـ System Prompt،
     * وتُعيد قائمة مصفاة ومقصوصة بعناية لا تتجاوز الـ Max Tokens المطلوبة للمحرك.
     */
    fun engineeringContext(
        systemPrompt: String,
        fullHistory: List<ChatMessage>,
        latestUserMessage: String
    ): List<ChatMessage> {
        val optimizedMessages = mutableListOf<ChatMessage>()
        
        // 1. احسب التوكنز المحجوزة للـ System Prompt والرسالة الأخيرة (أعلى أولويات)
        val systemTokens = estimateTokens(systemPrompt)
        val latestMessageTokens = estimateTokens(latestUserMessage)
        
        // نترك 256 توكن كأمان للمخرجات (Response Budget) لمنع أخطاء الـ Overflow
        var remainingTokens = MAX_ALLOWED_TOKENS - (systemTokens + latestMessageTokens + 256)
        
        Log.d(TAG, "Starting Context Engineering. Total Budget: $MAX_ALLOWED_TOKENS. Remaining tokens for history: $remainingTokens")

        // 2. معالجة التاريخ من الأحدث إلى الأقدم
        val keptHistory = mutableListOf<ChatMessage>()

        // 3. التمرير عبر التاريخ واختيار الأحدث فالأحدث طالما هناك مساحة (Sliding Window)
        for (message in fullHistory.reversed()) {
            val messageTokens = estimateTokens(message.text)
            if (remainingTokens >= messageTokens) {
                keptHistory.add(message)
                remainingTokens -= messageTokens
            } else {
                // توقف عند الوصول للحد الأقصى لحماية المحرك من البطء أو الانهيار
                Log.w(TAG, "Context limit approaching! Dropping ${fullHistory.size - keptHistory.size} older messages.")
                break
            }
        }

        // 4. إعادة ترتيب الرسائل المحفوظة زمنياً (من الأقدم للأحدث)
        optimizedMessages.addAll(keptHistory.reversed())
        
        Log.d(TAG, "Context engineered successfully. Total kept history messages: ${optimizedMessages.size}")
        return optimizedMessages
    }
}
