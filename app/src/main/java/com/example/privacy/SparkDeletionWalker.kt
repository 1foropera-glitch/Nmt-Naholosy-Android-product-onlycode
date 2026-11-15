package com.example.privacy

interface DeletionDataStore {
    /** Delete at most one page. True means the collection was already empty. */
    suspend fun deletePage(collectionPath: String): Boolean
    suspend fun deleteDocument(documentPath: String)
}

/** Auth may be deleted only after this returns true. Checkpoints advance after confirmed cleanup. */
suspend fun clearSparkAccountData(uid: String, nextEpoch: Long, generation: Long,
    store: DeletionDataStore, checkpoint: suspend (Long) -> Unit, pageBudget: Int = 20): Boolean {
    require(uid.isNotBlank() && '/' !in uid && generation in 0..Int.MAX_VALUE.toLong())
    require(nextEpoch in 0..generation + 1 && pageBudget > 0)
    val root = "users/$uid"
    var current = nextEpoch
    var pages = 0
    while (current <= generation && pages < pageBudget) {
        val epoch = "$root/epochs/$current"
        pages++
        if (store.deletePage("$epoch/wordProgress")) {
            store.deleteDocument(epoch)
            current++
            checkpoint(current)
        }
    }
    if (current <= generation) return false
    for (collection in listOf("wordProgress", "resetRequests", "entitlements")) {
        var empty = false
        while (pages < pageBudget) {
            pages++
            if (store.deletePage("$root/$collection")) { empty = true; break }
        }
        if (!empty) return false
    }
    store.deleteDocument(root)
    return true
}
