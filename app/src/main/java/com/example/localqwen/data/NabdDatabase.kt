package com.example.localqwen.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, LocalDocumentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NabdDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun localDocumentDao(): LocalDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: NabdDatabase? = null

        fun getInstance(context: Context): NabdDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NabdDatabase::class.java,
                    "nabd_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
