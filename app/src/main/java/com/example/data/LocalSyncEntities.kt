package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class LocalProfile(
    @PrimaryKey val id: String,
    val generation: Long = 0,
    val cloudVersion: Long = -1,
    val pendingResetId: String? = null,
    val writerId: String = java.util.UUID.randomUUID().toString(),
    val cloudBound: Boolean = false
)

@Entity(tableName = "active_profile")
data class ActiveProfile(@PrimaryKey val singleton: Int = 1, val profileId: String = "guest")

@Entity(tableName = "sync_outbox", primaryKeys = ["profileId", "wordId"])
data class PendingProgress(val profileId: String, val wordId: Int, val revision: Long)

@Entity(tableName = "learning_sessions")
data class StoredSession(@PrimaryKey val profileId: String, val json: String)

@Entity(tableName = "dictionary_metadata")
data class DictionaryMetadata(@PrimaryKey val singleton: Int = 1, val digest: String)
