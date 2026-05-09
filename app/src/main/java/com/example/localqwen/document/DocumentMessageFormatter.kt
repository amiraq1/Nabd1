package com.example.localqwen.document

object DocumentMessageFormatter {

    fun noSelectedDocumentMessage(): String = "لا يوجد مستند محدد"

    fun documentSelectedMessage(title: String): String = "تم اختيار: $title"

    fun documentClearedMessage(): String = "تم إلغاء اختيار المستند"

    fun emptyLibraryMessage(): String = "المكتبة فارغة"

    fun semanticSearchUsedStatus(): String = "تم استخدام البحث الدلالي • جاري التوليد..."

    fun keywordSearchUsedStatus(): String = "تم استخدام البحث النصي • جاري التوليد..."

    fun semanticFallbackStatus(): String =
        "البحث الدلالي غير جاهز، تم استخدام البحث النصي مؤقتًا.\nجاري التوليد..."

    fun semanticFallbackStatusWithReason(reason: String): String =
        "$reason\nتم استخدام البحث النصي مؤقتًا.\nجاري التوليد..."

    fun selectedDocumentStatus(title: String): String = "المستند المحدد: $title"

    fun documentDeletedMessage(title: String): String = "تم حذف المستند: $title"

    fun documentCopiedMessage(): String = "تم نسخ محتوى المستند"

    fun documentAnswerPrefix(): String = "إجابة من المستند:"

    fun sourcesHeader(): String = "المصادر المستخدمة"
}
