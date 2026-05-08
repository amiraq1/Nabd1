package com.example.localqwen.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ChatSessionEntity.class, LocalDocumentEntity.class}, version = 1, exportSchema = false)
public abstract class NabdDatabase extends RoomDatabase {
    public abstract ChatSessionDao chatSessionDao();
    public abstract LocalDocumentDao localDocumentDao();

    private static volatile NabdDatabase INSTANCE;

    public static NabdDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (NabdDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            NabdDatabase.class,
                            "nabd_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
