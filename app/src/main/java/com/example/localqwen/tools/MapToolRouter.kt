package com.example.localqwen.tools

/**
 * Detects map-related intents from user input (Arabic & English).
 */
object MapToolRouter {

    private val searchPatterns = listOf(
        Regex("^(?:افتح|اعرض|فتح)\\s+(?:الخريطة|خريطة)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:افتح|اعرض|فتح)\\s+(?:على\\s+)?(?:الخريطة|خريطة)\\s*[:\\-]?\\s*(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:اعرض\\s+على\\s+الخريطة)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:وين|فين|أين)\\s+(?:موقع|مكان)?\\s*(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:ابحث|بحث)\\s+(?:على\\s+)?(?:الخريطة|خريطة)\\s+(?:عن\\s+)?(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:map|open map|show on map)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    private val routePatterns = listOf(
        Regex("^(?:طريق|مسار|الطريق|المسار)\\s+من\\s+(.+?)\\s+(?:إلى|الى|ل)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:route|directions?)\\s+from\\s+(.+?)\\s+to\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    private val savePlacePatterns = listOf(
        Regex("^(?:حفظ|احفظ|سجل)\\s+(?:المكان|مكان)\\s+(.+?)\\s+(?:باسم|بإسم|اسمه)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:save place|save location)\\s+(.+?)\\s+(?:as|named)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    private val openSavedPlacePatterns = listOf(
        Regex("^(?:افتح|اعرض|فتح)\\s+(?:المكان|مكان)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:open place|open saved place)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    private val deletePlacePatterns = listOf(
        Regex("^(?:حذف|احذف|امسح)\\s+(?:المكان|مكان)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:delete place|remove place)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    private val listPlacesPatterns = listOf(
        Regex("^(?:عرض|اعرض|قائمة)\\s+(?:الأماكن|الاماكن)\\s+(?:المحفوظة)?", RegexOption.IGNORE_CASE),
        Regex("^(?:الأماكن|الاماكن)\\s+(?:المحفوظة)", RegexOption.IGNORE_CASE),
        Regex("^(?:list|show)\\s+(?:saved\\s+)?places", RegexOption.IGNORE_CASE)
    )

    fun detectMapIntent(input: String): MapToolIntent? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        // Route (check before search to avoid false positive)
        for (pattern in routePatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val origin = match.groupValues[1].trim()
                val destination = match.groupValues[2].trim()
                if (origin.isNotBlank() && destination.isNotBlank()) {
                    return MapToolIntent.RouteMap(origin, destination)
                }
            }
        }

        // Save place
        for (pattern in savePlacePatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val query = match.groupValues[1].trim()
                val name = match.groupValues[2].trim()
                if (query.isNotBlank() && name.isNotBlank()) {
                    return MapToolIntent.SavePlace(name, query)
                }
            }
        }

        // Delete place
        for (pattern in deletePlacePatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.isNotBlank()) {
                    return MapToolIntent.DeleteSavedPlace(name)
                }
            }
        }

        // List places
        for (pattern in listPlacesPatterns) {
            if (pattern.containsMatchIn(trimmed)) {
                return MapToolIntent.ListSavedPlaces
            }
        }

        // Open saved place
        for (pattern in openSavedPlacePatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.isNotBlank()) {
                    return MapToolIntent.OpenSavedPlace(name)
                }
            }
        }

        // Search (general map search — last priority)
        for (pattern in searchPatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val query = match.groupValues[1].trim()
                if (query.isNotBlank()) {
                    return MapToolIntent.SearchMap(query)
                }
            }
        }

        return null
    }
}
