package com.example.localqwen.document

object DocumentExtractionProcessor {
    
    enum class ExtractionMode(val label: String, val suffix: String) {
        RAW("نص خام", "(نص خام)"),
        MARKDOWN("Markdown منظم", "(Markdown)"),
        STRUCTURED("بيانات منظمة", "(بيانات منظمة)"),
        REDACTED("إزالة المعلومات الحساسة", "(منقح)")
    }

    fun redactSensitiveInfo(text: String): String {
        var redacted = text
        // Email pattern
        redacted = redacted.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"), "[بريد إلكتروني محذوف]")
        // Phone pattern (generic)
        redacted = redacted.replace(Regex("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4,6}"), "[رقم هاتف محذوف]")
        // Generic ID/Long numbers (8+ digits)
        redacted = redacted.replace(Regex("\\b\\d{8,20}\\b"), "[رقم تعريفي محذوف]")
        
        return redacted
    }

    fun getMarkdownPrompt(text: String): String {
        return """
            قم بتحويل النص التالي المستخرج عبر OCR إلى تنسيق Markdown منظم واحترافي.
            - استخدم العناوين (Headings) والقوائم (Bullet points).
            - حافظ على المعنى الأصلي بدقة.
            - لا تقم بإضافة أي معلومات خارجية أو حقائق غير موجودة في النص.
            - النص باللغة العربية.
            
            النص المستخرج:
            $text
        """.trimIndent()
    }

    fun getStructuredDataPrompt(text: String): String {
        return """
            استخرج البيانات الأساسية من النص التالي بتنسيق نقاط منظمة:
            - العنوان:
            - التاريخ:
            - الأسماء المذكورة:
            - المبالغ (إن وجدت):
            - ملخص قصير:
            - تفاصيل هامة أخرى:
            
            إذا كانت المعلومة غير متوفرة، اكتب "غير متوفر". لا تضف معلومات من خارج النص.
            
            النص:
            $text
        """.trimIndent()
    }

    fun getRedactionPrompt(text: String): String {
        return """
            قم بمراجعة النص التالي وحذف أي معلومات شخصية حساسة متبقية (مثل أسماء كاملة لأشخاص، عناوين سكنية دقيقة، أرقام سرية).
            استبدل المعلومة المحذوفة بكلمة [محذوف].
            حافظ على سياق النص العام.
            
            النص:
            $text
        """.trimIndent()
    }
}
