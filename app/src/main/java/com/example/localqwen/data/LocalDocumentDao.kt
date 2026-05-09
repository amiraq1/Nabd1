package com.example.localqwen.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalDocumentDao {
    @Query("SELECT * FROM local_documents ORDER BY createdAt DESC")
    suspend fun getAllDocuments(): List<LocalDocumentEntity>

    @Query("SELECT * FROM local_documents WHERE id = :id LIMIT 1")
    suspend fun getDocument(id: String): LocalDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(document: LocalDocumentEntity)

    @Query("DELETE FROM local_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM local_documents")
    suspend fun count(): Int
}
