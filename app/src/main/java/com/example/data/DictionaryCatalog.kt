package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.example.sync.cloudKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class DictionaryCatalog(context: Context) {
    private val assets = context.applicationContext.assets

    suspend fun update(db: AppDatabase) = withContext(Dispatchers.IO) {
        val bytes = assets.open("words.json").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if (db.syncDao().catalogDigest() == digest) return@withContext
        val type = Types.newParameterizedType(List::class.java, MoshiWord::class.java)
        val entries = requireNotNull(Moshi.Builder().build().adapter<List<MoshiWord>>(type).fromJson(bytes.toString(Charsets.UTF_8)))
        require(entries.isNotEmpty() && entries.map { it.key }.distinct().size == entries.size) { "Словник має порожні або повторні записи." }
        require(entries.all { it.key.matches(Regex("v1_[0-9a-f]{64}")) && it.stressPositions.isNotEmpty() &&
            it.stressPositions.all { pos -> pos in it.plain.indices } }) { "Некоректний формат словника." }
        db.withTransaction {
            if (db.syncDao().catalogDigest() != digest) {
                val old = db.wordDao().getCatalogIncludingRetired()
                val byKey = old.associateBy { it.cloudKey() }
                db.wordDao().retireCatalog()
                val words = entries.map { entry ->
                    Word(id = byKey[entry.key]?.id ?: 0, stressed = entry.stressed, plain = entry.plain,
                        normalized = entry.normalized, stressPositions = entry.stressPositions.joinToString(","),
                        letter = entry.letter, note = entry.note, source = entry.source, stableKey = entry.key)
                }
                db.wordDao().insertWords(words)
                db.syncDao().saveCatalogMetadata(DictionaryMetadata(digest = digest))
            }
        }
    }
}
