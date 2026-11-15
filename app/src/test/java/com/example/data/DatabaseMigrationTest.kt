package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseMigrationTest {
    @Test fun versionTwoDataSurvivesRoomSchemaValidationAndMigration() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE words (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            stressed TEXT NOT NULL, plain TEXT NOT NULL, normalized TEXT NOT NULL,
                            stressPositions TEXT NOT NULL, letter TEXT NOT NULL, note TEXT, source TEXT NOT NULL)""")
                        db.execSQL("""CREATE TABLE word_states (wordId INTEGER NOT NULL PRIMARY KEY,
                            box INTEGER NOT NULL, dueAt INTEGER, status TEXT NOT NULL,
                            correctCount INTEGER NOT NULL, wrongCount INTEGER NOT NULL, lapseCount INTEGER NOT NULL,
                            lastReviewedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL)""")
                        db.execSQL("INSERT INTO words VALUES (7, 'test', 'test', 'test', '1', 'T', NULL, 'test')")
                        db.execSQL("INSERT INTO word_states VALUES (7, 3, 1234, 'learning', 5, 2, 2, 1000, 1000, 7)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        helper.writableDatabase
        helper.close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3).build()
        try {
            val state = requireNotNull(migrated.wordDao().getWordState(7, "legacy"))
            assertEquals(3, state.box)
            assertEquals(5, state.correctCount)
            assertEquals(2, state.wrongCount)
            assertEquals(7L, state.revision)
            assertEquals("test", migrated.wordDao().getWordById(7)?.plain)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
