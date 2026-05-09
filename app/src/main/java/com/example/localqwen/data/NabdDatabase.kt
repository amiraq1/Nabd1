package com.example.localqwen.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatSessionEntity::class, LocalDocumentEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NabdDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun localDocumentDao(): LocalDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: NabdDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Rebuild chat_sessions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_sessions_new` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `messagesText` TEXT NOT NULL, 
                        `lastAssistantResponse` TEXT, 
                        `selectedDocumentId` TEXT, 
                        `documentAnswerLength` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO chat_sessions_new (
                        id, title, createdAt, updatedAt, messagesText, 
                        lastAssistantResponse, selectedDocumentId, documentAnswerLength
                    )
                    SELECT 
                        id, COALESCE(title, 'محادثة بدون عنوان'), createdAt, updatedAt, COALESCE(messagesText, '[]'), 
                        lastAssistantResponse, selectedDocumentId, documentAnswerLength
                    FROM chat_sessions
                """.trimIndent())
                
                db.execSQL("DROP TABLE chat_sessions")
                db.execSQL("ALTER TABLE chat_sessions_new RENAME TO chat_sessions")

                // 2. Rebuild local_documents table (just in case it has similar nullability issues)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_documents_new` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `extractedText` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO local_documents_new (id, title, type, extractedText, createdAt)
                    SELECT id, COALESCE(title, 'مستند بدون عنوان'), COALESCE(type, 'pdf'), COALESCE(extractedText, ''), createdAt
                    FROM local_documents
                """.trimIndent())
                
                db.execSQL("DROP TABLE local_documents")
                db.execSQL("ALTER TABLE local_documents_new RENAME TO local_documents")
            }
        }

        fun getInstance(context: Context): NabdDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NabdDatabase::class.java,
                    "nabd_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
        }
    }
}
