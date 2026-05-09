package com.example.localqwen.ui

object NabdErrorMessages {
    fun modelLoadFailed(error: String? = null): String {
        return "تعذر تشغيل النموذج. تأكد من استيراد ملف النموذج الصحيح، أو جرّب نموذجًا أخف."
    }

    fun generationFailed(error: String? = null): String {
        return "حدث خطأ أثناء توليد الرد. جرّب إعادة تشغيل نبض أو استخدام نموذج أصغر."
    }

    fun pdfFailed(error: String? = null): String {
        return "تعذر تحليل ملف PDF. جرّب ملفًا أصغر أو ملفًا يحتوي على نص واضح."
    }

    fun ocrNoText(): String {
        return "لم يتم العثور على نص واضح في الصورة. جرّب صورة أوضح وبإضاءة أفضل."
    }

    fun visionModelMissing(): String {
        return "نموذج الرؤية غير مستورد. سيتم استخدام استخراج النص (OCR) بدلاً منه."
    }

    fun semanticIndexMissing(): String {
        return "لا يوجد فهرس دلالي لهذا المستند. أنشئ الفهرس أولاً أو استخدم البحث النصي."
    }

    fun mapAppMissing(): String {
        return "لا يوجد تطبيق خرائط متاح على جهازك."
    }

    fun fileTooLarge(): String {
        return "الملف كبير جدًا للمعالجة الحالية. جرّب ملفًا بحجم أصغر."
    }

    fun unsupportedFile(): String {
        return "صيغة الملف غير مدعومة. يرجى اختيار ملف متوافق."
    }

    fun engineNotReady(): String {
        return "نبض غير جاهز. يرجى تشغيل النموذج أولاً."
    }

    fun noDocumentSelected(): String {
        return "لم يتم اختيار مستند. يرجى اختيار مستند من المكتبة أولاً."
    }

    fun embeddingModelMissing(): String {
        return "نموذج التضمين غير متوفر. يرجى استيراد نموذج التضمين لاستخدام ميزات البحث المتقدم."
    }
}
