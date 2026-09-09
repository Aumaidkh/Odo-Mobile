package com.hopcape.logging.internal.file

import com.hopcape.logging.api.LogFileNaming
import com.hopcape.logging.api.LogFileStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryLogFileStoreTest {

    private val stats = LogFileStats(lineCount = 2, warnCount = 0, errorCount = 0, hadFatal = false)

    @Test
    fun sealActive_withNothingWritten_returnsNull() {
        val store = InMemoryLogFileStore(nowMs = { 0L })

        assertNull(store.sealActive(stats))
        assertTrue(store.listSealed().isEmpty())
    }

    @Test
    fun appendThenSeal_producesASealedHandleReadableByName() {
        var clock = 1_000L
        val store = InMemoryLogFileStore(nowMs = { clock })

        store.appendToActive(listOf("line one"))
        store.appendToActive(listOf("line two"))
        clock = 5_000L
        val handle = store.sealActive(stats)

        checkNotNull(handle)
        assertEquals(LogFileNaming.sealedFileName(LogFileNaming.activeFileName(1_000L)), handle.name)
        assertEquals(1_000L, handle.openedAtMs)
        assertEquals(5_000L, handle.sealedAtMs)
        assertEquals(stats, handle.stats)

        val bytes = store.read(handle.name)
        checkNotNull(bytes)
        assertEquals("line one\nline two", bytes.decodeToString())
        assertEquals(handle.sizeBytes, bytes.size.toLong())
    }

    @Test
    fun sealActive_twiceInARow_secondCallHasNothingToSeal() {
        val store = InMemoryLogFileStore(nowMs = { 0L })

        store.appendToActive(listOf("only line"))
        assertTrue(store.sealActive(stats) != null)
        assertNull(store.sealActive(stats))
    }

    @Test
    fun appendAfterSeal_opensAFreshFileRatherThanReusingTheSealedOne() {
        var clock = 1_000L
        val store = InMemoryLogFileStore(nowMs = { clock })

        store.appendToActive(listOf("session one"))
        val first = store.sealActive(stats)
        clock = 2_000L
        store.appendToActive(listOf("session two"))
        val second = store.sealActive(stats)

        checkNotNull(first); checkNotNull(second)
        assertTrue(first.name != second.name)
        assertEquals(2, store.listSealed().size)
    }

    @Test
    fun delete_removesTheFileAndItsBytes_andDropsItFromTotalBytes() {
        val store = InMemoryLogFileStore(nowMs = { 0L })
        store.appendToActive(listOf("some content"))
        val handle = checkNotNull(store.sealActive(stats))

        assertEquals(handle.sizeBytes, store.totalBytes())

        store.delete(handle.name)

        assertNull(store.read(handle.name))
        assertTrue(store.listSealed().isEmpty())
        assertEquals(0L, store.totalBytes())
    }

    @Test
    fun sealOrphans_alwaysEmpty_sinceInMemoryStateDoesNotSurviveAProcessRestart() {
        val store = InMemoryLogFileStore(nowMs = { 0L })
        store.appendToActive(listOf("still active"))

        assertTrue(store.sealOrphans().isEmpty())
    }

    @Test
    fun activeFileName_isNullUntilSomethingIsWritten_thenMatchesTheEventualSealedStem() {
        val store = InMemoryLogFileStore(nowMs = { 1_000L })
        assertNull(store.activeFileName())

        store.appendToActive(listOf("line one"))
        val activeName = checkNotNull(store.activeFileName())

        assertEquals(LogFileNaming.sealedFileName(activeName), checkNotNull(store.sealActive(stats)).name)
    }

    @Test
    fun activeFileName_isNullAgainAfterSealing() {
        val store = InMemoryLogFileStore(nowMs = { 0L })
        store.appendToActive(listOf("x"))
        store.sealActive(stats)

        assertNull(store.activeFileName())
    }
}
