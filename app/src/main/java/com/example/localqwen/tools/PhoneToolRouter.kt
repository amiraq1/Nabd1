package com.example.localqwen.tools

enum class PhoneToolIntent {
    BATTERY,
    DEVICE_INFO,
    STORAGE,
    INSTALLED_APPS
}

object PhoneToolRouter {

    private val batteryKeywords = listOf("بطارية", "البطارية", "شحن", "نسبة الشحن", "battery", "charge")
    private val deviceKeywords = listOf("معلومات جهازي", "جهازي", "نوع الجهاز", "موديل", "إصدار أندرويد", "android version", "device info", "model")
    private val storageKeywords = listOf("مساحة", "التخزين", "ذاكرة", "المساحة المتبقية", "storage", "free space")
    private val appsKeywords = listOf("كم تطبيق", "التطبيقات", "تطبيقات مثبتة", "installed apps", "apps count")

    fun detectToolIntents(input: String): List<PhoneToolIntent> {
        val intents = mutableListOf<PhoneToolIntent>()
        val inputLow = input.lowercase()

        if (batteryKeywords.any { inputLow.contains(it) }) intents.add(PhoneToolIntent.BATTERY)
        if (deviceKeywords.any { inputLow.contains(it) }) intents.add(PhoneToolIntent.DEVICE_INFO)
        if (storageKeywords.any { inputLow.contains(it) }) intents.add(PhoneToolIntent.STORAGE)
        if (appsKeywords.any { inputLow.contains(it) }) intents.add(PhoneToolIntent.INSTALLED_APPS)

        return intents.distinct()
    }
}
