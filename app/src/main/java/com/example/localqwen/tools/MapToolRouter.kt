package com.example.localqwen.tools

sealed class MapToolIntent {
    data class SearchMap(val query: String) : MapToolIntent()
    data class RouteMap(val origin: String, val destination: String) : MapToolIntent()
    data class SavePlace(val name: String, val query: String) : MapToolIntent()
    data class OpenSavedPlace(val name: String) : MapToolIntent()
    data class DeleteSavedPlace(val name: String) : MapToolIntent()
    object ListSavedPlaces : MapToolIntent()
}

object MapToolRouter {
    private val mapKeywords = listOf("افتح الخريطة", "اعرض على الخريطة", "وين موقع", "map", "open map", "ابحث في الخريطة عن")

    private val routePatterns = listOf(
        Regex("طريق من\\s+(.+)\\s+إلى\\s+(.+)"),
        Regex("مسار من\\s+(.+)\\s+إلى\\s+(.+)"),
        Regex("route from\\s+(.+)\\s+to\\s+(.+)"),
        Regex("directions from\\s+(.+)\\s+to\\s+(.+)")
    )

    private val savePatterns = listOf(
        Regex("حفظ المكان\\s+(.+)\\s+باسم\\s+(.+)"),
        Regex("احفظ المكان\\s+(.+)\\s+باسم\\s+(.+)")
    )

    private val openSavedPatterns = listOf(
        Regex("افتح المكان\\s+(.+)"),
        Regex("اعرض المكان المحفوظ\\s+(.+)")
    )

    private val deleteSavedPatterns = listOf(
        Regex("حذف المكان\\s+(.+)"),
        Regex("احذف المكان\\s+(.+)")
    )

    fun detectMapIntent(input: String): MapToolIntent? {
        val inputLow = input.lowercase().trim()

        if (inputLow == "عرض الأماكن المحفوظة" || inputLow == "الاماكن المحفوظة" || inputLow == "الخريطة المحفوظة" || inputLow == "الأماكن المحفوظة") {
            return MapToolIntent.ListSavedPlaces
        }

        for (pattern in savePatterns) {
            val match = pattern.find(input)
            if (match != null) {
                return MapToolIntent.SavePlace(match.groupValues[2].trim(), match.groupValues[1].trim())
            }
        }

        for (pattern in routePatterns) {
            val match = pattern.find(inputLow)
            if (match != null) {
                return MapToolIntent.RouteMap(match.groupValues[1].trim(), match.groupValues[2].trim())
            }
        }

        for (pattern in deleteSavedPatterns) {
            val match = pattern.find(input)
            if (match != null) {
                return MapToolIntent.DeleteSavedPlace(match.groupValues[1].trim())
            }
        }

        for (pattern in openSavedPatterns) {
            val match = pattern.find(input)
            if (match != null) {
                return MapToolIntent.OpenSavedPlace(match.groupValues[1].trim())
            }
        }

        if (mapKeywords.any { inputLow.contains(it) }) {
            var query = input
            for (keyword in mapKeywords) {
                if (inputLow.contains(keyword)) {
                    val idx = inputLow.indexOf(keyword)
                    val extracted = input.substring(idx + keyword.length).trim()
                    if (extracted.isNotBlank()) {
                        query = extracted
                        break
                    }
                }
            }
            return MapToolIntent.SearchMap(query.takeIf { it.isNotBlank() } ?: input)
        }
        
        return null
    }
}
