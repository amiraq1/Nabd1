package com.example.localqwen.memory

import android.content.Context
import com.example.localqwen.data.SecurePreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MemoryStore(private val context: Context) {
    private val preferences = SecurePreferences.get(context)


    fun getAllMemories(): List<MemoryItem> {
        val raw = preferences.getString(KEY_MEMORY_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        MemoryItem(
                            id = obj.getString("id"),
                            text = obj.getString("text"),
                            createdAt = obj.getLong("createdAt"),
                            updatedAt = obj.getLong("updatedAt"),
                            category = obj.optString("category", CATEGORY_GENERAL)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addMemory(text: String, category: String = CATEGORY_GENERAL) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val memories = getAllMemories().toMutableList()
        memories.add(MemoryItem(UUID.randomUUID().toString(), text, now, now, category))
        saveList(memories)
    }

    fun updateMemory(id: String, newText: String): Boolean {
        if (newText.isBlank()) return false
        val memories = getAllMemories()
        val found = memories.any { it.id == id }
        if (!found) return false
        
        val now = System.currentTimeMillis()
        val updated = memories.map {
            if (it.id == id) it.copy(text = newText, updatedAt = now) else it
        }
        saveList(updated)
        return true
    }

    fun deleteMemory(id: String) {
        val memories = getAllMemories().filter { it.id != id }
        saveList(memories)
    }

    fun clearAll() {
        preferences.edit().remove(KEY_MEMORY_JSON).apply()
    }

    fun isMemoryEnabled(): Boolean {
        return preferences.getBoolean(KEY_MEMORY_ENABLED, true)
    }

    fun setMemoryEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply()
    }

    private fun saveList(list: List<MemoryItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
                put("category", item.category)
            }
            array.put(obj)
        }
        preferences.edit().putString(KEY_MEMORY_JSON, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "nabd_memory"
        private const val KEY_MEMORY_JSON = "memory_json"
        private const val KEY_MEMORY_ENABLED = "memory_enabled"
        
        const val CATEGORY_GENERAL = "general"
        const val CATEGORY_PERSONAL = "personal"
        const val CATEGORY_PROJECT = "project"
        const val CATEGORY_PREFERENCE = "preference"
        const val CATEGORY_PROFILE = "profile"
    }
}
