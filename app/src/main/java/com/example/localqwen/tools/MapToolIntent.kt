package com.example.localqwen.tools

sealed class MapToolIntent {
    data class SearchMap(val query: String) : MapToolIntent()
    data class RouteMap(val origin: String, val destination: String) : MapToolIntent()
    data class SavePlace(val name: String, val query: String) : MapToolIntent()
    data class OpenSavedPlace(val name: String) : MapToolIntent()
    data class DeleteSavedPlace(val name: String) : MapToolIntent()
    data object ListSavedPlaces : MapToolIntent()
}
