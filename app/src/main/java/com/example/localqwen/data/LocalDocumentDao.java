package com.example.localqwen.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocalDocumentDao {
    @Query("SELECT * FROM local_documents ORDER BY createdAt DESC")
    List<LocalDocumentEntity> getAllDocuments();

    @Query("SELECT * FROM local_documents WHERE id = :id LIMIT 1")
    LocalDocumentEntity getDocument(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(LocalDocumentEntity document);

    @Query("DELETE FROM local_documents WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT COUNT(*) FROM local_documents")
    int count();
}
