package com.example.localqwen.tools

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlace(
    val name: String,
    val query: String,
    val createdAt: Long = System.currentTimeMillis()
)

class SavedPlacesStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nabd_prefs", Context.MODE_PRIVATE)

    fun getPlaces(): List<SavedPlace> {
        val jsonStr = prefs.getString("saved_map_places_json", "[]") ?: "[]"
        val places = mutableListOf<SavedPlace>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                places.add(SavedPlace(
                    name = obj.getString("name"),
                    query = obj.getString("query"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        } catch (e: Exception) { }
        return places.sortedBy { it.createdAt }
    }

    fun savePlace(place: SavedPlace) {
        val places = getPlaces().toMutableList()
        val index = places.indexOfFirst { it.name.lowercase() == place.name.lowercase() }
        if (index >= 0) {
            places[index] = place
        } else {
            places.add(place)
        }
        saveAll(places)
    }

    fun deletePlace(name: String) {
        val places = getPlaces().filter { it.name.lowercase() != name.lowercase() }
        saveAll(places)
    }

    fun getPlace(name: String): SavedPlace? {
        return getPlaces().find { it.name.lowercase() == name.lowercase() }
    }

    private fun saveAll(places: List<SavedPlace>) {
        val arr = JSONArray()
        places.forEach {
            val obj = JSONObject().apply {
                put("name", it.name)
                put("query", it.query)
                put("createdAt", it.createdAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString("saved_map_places_json", arr.toString()).apply()
    }
}
