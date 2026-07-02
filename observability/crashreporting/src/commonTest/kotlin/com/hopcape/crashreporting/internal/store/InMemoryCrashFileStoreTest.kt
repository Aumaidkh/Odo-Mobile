package com.hopcape.crashreporting.internal.store

import com.hopcape.crashreporting.testReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryCrashFileStoreTest {

    @Test
    fun writeThenReadReturnsEquivalentReports() {
        val store = InMemoryCrashFileStore()
        val a = testReport(crashId = "a")
        val b = testReport(crashId = "b")

        store.writeSync(a)
        store.writeSync(b)

        val pending = store.readPending().associateBy { it.crashId }
        assertEquals(setOf("a", "b"), pending.keys)
        assertEquals(a, pending["a"])
    }

    @Test
    fun clearRemovesOnlyTheNamedReport() {
        val store = InMemoryCrashFileStore()
        store.writeSync(testReport(crashId = "a"))
        store.writeSync(testReport(crashId = "b"))

        store.clear("a")

        assertEquals(listOf("b"), store.readPending().map { it.crashId })
    }

    @Test
    fun readPendingIsEmptyInitially() {
        assertTrue(InMemoryCrashFileStore().readPending().isEmpty())
    }
}
