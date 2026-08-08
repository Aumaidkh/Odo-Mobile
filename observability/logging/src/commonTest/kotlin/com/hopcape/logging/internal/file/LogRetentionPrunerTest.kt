package com.hopcape.logging.internal.file

import com.hopcape.logging.api.FileLoggingConfig
import com.hopcape.logging.api.LogFileStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogRetentionPrunerTest {

    private val stats = LogFileStats(lineCount = 1, warnCount = 0, errorCount = 0, hadFatal = false)
    private var clock = 0L
    private val store = InMemoryLogFileStore(nowMs = { clock })

    private fun sealFileAt(atMs: Long, sizeBytes: Int) {
        clock = atMs
        store.appendToActive(listOf("x".repeat(sizeBytes)))
        checkNotNull(store.sealActive(stats))
    }

    /** A retention config where nothing but the dimension under test can fire. */
    private fun permissiveExcept(
        maxAgeDays: Int = 365,
        maxTotalBytes: Long = Long.MAX_VALUE,
        maxFileCount: Int = Int.MAX_VALUE,
    ) = FileLoggingConfig.RetentionPolicy(maxAgeDays, maxTotalBytes, maxFileCount)

    @Test
    fun pruneByAge_deletesOnlyFilesSealedBeforeTheCutoff() {
        sealFileAt(atMs = 0L, sizeBytes = 10)
        sealFileAt(atMs = 10_000_000L, sizeBytes = 10)
        sealFileAt(atMs = 50_000_000L, sizeBytes = 10)
        clock = 100_000_000L // "now" for the prune pass itself

        val pruner = LogRetentionPruner(store, permissiveExcept(maxAgeDays = 1), nowMs = { clock })
        pruner.prune()

        // cutoff = 100_000_000 - 86_400_000 = 13_600_000 — only the 50_000_000 file survives.
        assertEquals(1, store.listSealed().size)
        assertEquals(50_000_000L, store.listSealed().single().sealedAtMs)
    }

    @Test
    fun pruneByTotalSize_deletesOldestFirstUntilUnderTheCap() {
        // Spaced >= 1s apart: LogFileNaming's stem has 1-second resolution, so two files
        // opened in the same UTC second would collide onto one name.
        sealFileAt(atMs = 0L, sizeBytes = 60)
        sealFileAt(atMs = 2_000L, sizeBytes = 60)
        sealFileAt(atMs = 4_000L, sizeBytes = 60)

        val pruner = LogRetentionPruner(store, permissiveExcept(maxTotalBytes = 100L), nowMs = { clock })
        pruner.prune()

        assertEquals(1, store.listSealed().size)
        assertEquals(4_000L, store.listSealed().single().sealedAtMs)
        assertTrue(store.totalBytes() <= 100L)
    }

    @Test
    fun pruneByCount_deletesOldestFirstUntilAtTheLimit() {
        sealFileAt(atMs = 0L, sizeBytes = 5)
        sealFileAt(atMs = 2_000L, sizeBytes = 5)
        sealFileAt(atMs = 4_000L, sizeBytes = 5)
        sealFileAt(atMs = 6_000L, sizeBytes = 5)

        val pruner = LogRetentionPruner(store, permissiveExcept(maxFileCount = 2), nowMs = { clock })
        pruner.prune()

        val remaining = store.listSealed().map { it.sealedAtMs }.sorted()
        assertEquals(listOf(4_000L, 6_000L), remaining)
    }

    @Test
    fun prune_appliesAgeThenSizeThenCount_sequentiallyAgainstTheUpdatedSet() {
        sealFileAt(atMs = 0L, sizeBytes = 5) // ancient — removed by age
        sealFileAt(atMs = 90_000_000L, sizeBytes = 5)
        sealFileAt(atMs = 91_000_000L, sizeBytes = 5)
        clock = 100_000_000L

        // Age removes the file at 0L, leaving 2. maxFileCount = 1 then must remove one more
        // from what's LEFT after age pruning, not from the original 3 — proves the count
        // pass re-reads the store rather than working off a stale snapshot.
        val pruner = LogRetentionPruner(
            store,
            FileLoggingConfig.RetentionPolicy(maxAgeDays = 1, maxTotalBytes = Long.MAX_VALUE, maxFileCount = 1),
            nowMs = { clock },
        )
        pruner.prune()

        assertEquals(1, store.listSealed().size)
        assertEquals(91_000_000L, store.listSealed().single().sealedAtMs)
    }
}
