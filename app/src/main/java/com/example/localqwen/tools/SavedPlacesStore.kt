package com.example.localqwen.tools

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists saved map places using SharedPreferences (JSON array).
 */
class SavedPlacesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPlaces(): List<SavedPlace> {
        val json = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        return runCatching { parseList(json) }.getOrDefault(emptyList())
    }

    fun savePlace(place: SavedPlace) {
        val places = getPlaces().toMutableList()
        places.removeAll { it.name.equals(place.name, ignoreCase = true) }
        places.add(0, place)
        persist(places)
    }

    fun getPlace(name: String): SavedPlace? {
        return getPlaces().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun deletePlace(name: String): Boolean {
        val places = getPlaces().toMutableList()
        val removed = places.removeAll { it.name.equals(name, ignoreCase = true) }
        if (removed) persist(places)
        return removed
    }

    private fun persist(places: List<SavedPlace>) {
        val array = JSONArray()
        for (place in places) {
            array.put(JSONObject().apply {
                put("name", place.name)
                put("query", place.query)
                put("createdAt", place.createdAt)
            })
        }
        prefs.edit().putString(KEY_PLACES, array.toString()).apply()
    }

    private fun parseList(json: String): List<SavedPlace> {
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            SavedPlace(
                name = obj.getString("name"),
                query = obj.getString("query"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "nabd_prefs"
        private const val KEY_PLACES = "saved_map_places_json"
    }
}
