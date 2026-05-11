package com.example.localqwen.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MemoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllMemories(): List<MemoryItem> {
        val raw = preferences.getString(KEY_MEMORY_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id", "").trim()
                    val text = item.optString("text", "").trim()
                    if (id.isBlank() || text.isBlank()) continue
                    add(
                        MemoryItem(
                            id = id,
                            text = text,
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                            category = item.optString("category", CATEGORY_GENERAL)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
    }

    fun addMemory(text: String, category: String = CATEGORY_GENERAL) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return

        val now = System.currentTimeMillis()
        val items = getAllMemories().toMutableList()
        val existingIndex = items.indexOfFirst { it.text.equals(normalized, ignoreCase = true) }
        if (existingIndex >= 0) {
            val existing = items[existingIndex]
            items[existingIndex] = existing.copy(
                updatedAt = now,
                category = normalizeCategory(category)
            )
        } else {
            items.add(
                MemoryItem(
                    id = UUID.randomUUID().toString(),
                    text = normalized,
                    createdAt = now,
                    updatedAt = now,
                    category = normalizeCategory(category)
                )
            )
        }
        saveMemories(items)
    }

    fun deleteMemory(id: String) {
        if (id.isBlank()) return
        val updated = getAllMemories().filterNot { it.id == id }
        saveMemories(updated)
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

    private fun saveMemories(items: List<MemoryItem>) {
        val array = JSONArray()
        items.forEach { memory ->
            array.put(
                JSONObject().apply {
                    put("id", memory.id)
                    put("text", memory.text)
                    put("createdAt", memory.createdAt)
                    put("updatedAt", memory.updatedAt)
                    put("category", normalizeCategory(memory.category))
                }
            )
        }
        preferences.edit().putString(KEY_MEMORY_JSON, array.toString()).apply()
    }

    private fun normalizeCategory(category: String): String {
        return when (category.lowercase()) {
            CATEGORY_PREFERENCE -> CATEGORY_PREFERENCE
            CATEGORY_PROFILE -> CATEGORY_PROFILE
            CATEGORY_PROJECT -> CATEGORY_PROJECT
            else -> CATEGORY_GENERAL
        }
    }

    companion object {
        const val KEY_MEMORY_JSON = "nabd_memory_json"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val PREFS_NAME = "nabd_prefs"

        const val CATEGORY_PREFERENCE = "preference"
        const val CATEGORY_PROFILE = "profile"
        const val CATEGORY_PROJECT = "project"
        const val CATEGORY_GENERAL = "general"
    }
}
