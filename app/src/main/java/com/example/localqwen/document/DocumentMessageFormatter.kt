package com.example.localqwen.document

object DocumentMessageFormatter {

    fun noSelectedDocumentMessage(): String = "لا يوجد مستند محدد"

    fun documentSelectedMessage(title: String): String = "تم اختيار: $title"

    fun documentClearedMessage(): String = "تم إلغاء اختيار المستند"

    fun emptyLibraryMessage(): String = "المكتبة فارغة"

    fun semanticSearchUsedStatus(): String = "بحث دلالي • جاري التوليد..."

    fun keywordSearchUsedStatus(): String = "بحث نصي • جاري التوليد..."

    fun semanticFallbackStatus(): String =
        "بحث نصي (البحث الدلالي غير جاهز) • جاري التوليد..."

    fun semanticFallbackStatusWithReason(reason: String): String =
        "بحث نصي ($reason) • جاري التوليد..."

    fun selectedDocumentStatus(title: String): String = "المستند المحدد: $title"

    fun documentDeletedMessage(title: String): String = "تم حذف المستند: $title"

    fun documentCopiedMessage(): String = "تم نسخ محتوى المستند"

    fun documentAnswerPrefix(): String = "إجابة من المستند:"

    fun sourcesHeader(): String = "المصادر المستخدمة"
}
