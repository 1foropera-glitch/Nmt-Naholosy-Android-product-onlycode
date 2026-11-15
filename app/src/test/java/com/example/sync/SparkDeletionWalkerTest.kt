package com.example.sync

import com.example.privacy.DeletionDataStore
import com.example.privacy.clearSparkAccountData
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SparkDeletionWalkerTest {
    private class Store : DeletionDataStore {
        val collections = mutableMapOf<String, Int>()
        val deleted = mutableListOf<String>()
        var interruptAfterPage = false
        override suspend fun deletePage(collectionPath: String): Boolean {
            val count = collections[collectionPath] ?: 0
            if (count == 0) return true
            collections[collectionPath] = (count - 100).coerceAtLeast(0)
            if (interruptAfterPage) { interruptAfterPage = false; throw IOException("lost response after commit") }
            return false
        }
        override suspend fun deleteDocument(documentPath: String) {
            assertFalse(collections.any { (path, count) -> path.startsWith("$documentPath/") && count > 0 })
            deleted += documentPath
        }
    }
    @Test fun interruptedPageResumesAndNeverDeletesParentBeforeChildren() = runTest {
        val store = Store()
        store.collections["users/test/epochs/0/wordProgress"] = 233
        store.collections["users/test/epochs/2/wordProgress"] = 17
        store.collections["users/test/resetRequests"] = 205
        store.collections["users/test/entitlements"] = 1
        store.interruptAfterPage = true
        var checkpoint = 0L
        try {
            clearSparkAccountData("test", checkpoint, 2, store, { checkpoint = it })
            fail("Must preserve interrupted operation")
        } catch (_: IOException) { }
        assertEquals(0L, checkpoint)
        assertTrue(store.deleted.isEmpty())
        assertFalse(clearSparkAccountData("test", checkpoint, 2, store, { checkpoint = it }, pageBudget = 4))
        assertFalse(store.deleted.contains("users/test"))
        assertTrue(clearSparkAccountData("test", checkpoint, 2, store, { checkpoint = it }))
        assertEquals(3L, checkpoint)
        assertTrue(store.collections.values.all { it == 0 })
        assertEquals("users/test", store.deleted.last())
        // A retry after root deletion but before saving cloudCleared remains safe.
        assertTrue(clearSparkAccountData("test", checkpoint, 2, store, { checkpoint = it }))
    }
    @Test fun tinyBudgetDoesNotReportCompletionBeforeAllCollectionsAreChecked() = runTest {
        val store = Store()
        store.collections["users/test/wordProgress"] = 101
        assertFalse(clearSparkAccountData("test", 1, 0, store, {}, pageBudget = 1))
        assertFalse(store.deleted.contains("users/test"))
        assertEquals(1, store.collections["users/test/wordProgress"])
        assertTrue(clearSparkAccountData("test", 1, 0, store, {}))
    }
}
