package com.example.localqwen.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessions(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun count(): Int
}
