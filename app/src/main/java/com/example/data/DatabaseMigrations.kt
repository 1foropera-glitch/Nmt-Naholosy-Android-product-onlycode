package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE words ADD COLUMN stableKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE words ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            db.execSQL("""CREATE TABLE IF NOT EXISTS word_states_v3 (
                wordId INTEGER NOT NULL, box INTEGER NOT NULL, dueAt INTEGER, status TEXT NOT NULL,
                correctCount INTEGER NOT NULL, wrongCount INTEGER NOT NULL, lapseCount INTEGER NOT NULL,
                lastReviewedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL,
                profileId TEXT NOT NULL, deviceId TEXT NOT NULL, countersJson TEXT NOT NULL,
                PRIMARY KEY(profileId, wordId))""")
            db.execSQL("""INSERT INTO word_states_v3 SELECT wordId, box, dueAt, status, correctCount,
                wrongCount, lapseCount, lastReviewedAt, updatedAt, revision, 'legacy', 'legacy', '{}'
                FROM word_states""")
            db.execSQL("DROP TABLE word_states")
            db.execSQL("ALTER TABLE word_states_v3 RENAME TO word_states")
            db.execSQL("""CREATE TABLE IF NOT EXISTS profiles (id TEXT NOT NULL PRIMARY KEY,
                generation INTEGER NOT NULL, cloudVersion INTEGER NOT NULL, pendingResetId TEXT,
                writerId TEXT NOT NULL, cloudBound INTEGER NOT NULL)""")
            db.execSQL("CREATE TABLE IF NOT EXISTS active_profile (singleton INTEGER NOT NULL PRIMARY KEY, profileId TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (profileId TEXT NOT NULL, wordId INTEGER NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(profileId, wordId))")
            db.execSQL("CREATE TABLE IF NOT EXISTS learning_sessions (profileId TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS dictionary_metadata (singleton INTEGER NOT NULL PRIMARY KEY, digest TEXT NOT NULL)")
        }
    }
}
