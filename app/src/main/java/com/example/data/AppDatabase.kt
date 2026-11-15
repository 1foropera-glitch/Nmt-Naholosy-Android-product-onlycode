package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Word::class, WordState::class, LocalProfile::class, ActiveProfile::class,
    PendingProgress::class, StoredSession::class, DictionaryMetadata::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun syncDao(): LocalSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "naholosy_database"
                )
                    .addMigrations(MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE word_states ADD COLUMN lapseCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE word_states ADD COLUMN lastReviewedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE word_states ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE word_states ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE word_states SET lapseCount = wrongCount, revision = correctCount + wrongCount")
            }
        }
    }
}
