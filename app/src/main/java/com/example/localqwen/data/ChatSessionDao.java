package com.example.localqwen.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    List<ChatSessionEntity> getAllSessions();

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    ChatSessionEntity getSession(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ChatSessionEntity session);

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT COUNT(*) FROM chat_sessions")
    int count();
}
