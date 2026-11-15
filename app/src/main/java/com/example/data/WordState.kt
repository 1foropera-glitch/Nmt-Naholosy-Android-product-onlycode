package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_states", primaryKeys = ["profileId", "wordId"])
data class WordState(
    val wordId: Int,
    val box: Int = 1,
    val dueAt: Long?, // Null if not started or completely learned (Box 7 success)
    val status: String = "learning", // "learning", "learned"
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lapseCount: Int = 0,
    val lastReviewedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val revision: Long = 0L,
    val profileId: String = "guest",
    val deviceId: String = "legacy",
    val countersJson: String = "{}"
)
