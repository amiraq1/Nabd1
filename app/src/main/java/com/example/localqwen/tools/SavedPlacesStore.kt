package com.example.localqwen.tools

import android.content.Context
import com.example.localqwen.data.SecurePreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists saved map places using SharedPreferences (JSON array).
 */
class SavedPlacesStore(context: Context) {
    private val prefs = SecurePreferences.get(context)


    fun getPlaces(): List<SavedPlace> {
        val json = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        return runCatching { parseList(json) }.getOrDefault(emptyList())
    }

    fun addPlace(place: SavedPlace) {
        val list = getPlaces().toMutableList()
        list.add(place)
        saveList(list)
    }

    fun deletePlace(name: String) {
        val list = getPlaces().filter { it.name != name }
        saveList(list)
    }

    private fun saveList(list: List<SavedPlace>) {
        val array = JSONArray()
        list.forEach { p ->
            val obj = JSONObject().apply {
                put("name", p.name)
                put("query", p.query)
                put("createdAt", p.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLACES, array.toString()).apply()
    }

    private fun parseList(json: String): List<SavedPlace> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(SavedPlace(
                    o.getString("name"),
                    o.getString("query"),
                    o.getLong("createdAt")
                ))
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "saved_places"
        private const val KEY_PLACES = "places_json"
    }
}
