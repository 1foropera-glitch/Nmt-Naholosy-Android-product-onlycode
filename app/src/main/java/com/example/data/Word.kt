package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stressed: String,
    val plain: String,
    val normalized: String,
    val stressPositions: String, // Comma separated integers, e.g. "3,5"
    val letter: String,
    val note: String?,
    val source: String,
    val stableKey: String = "",
    val isActive: Boolean = true
)
