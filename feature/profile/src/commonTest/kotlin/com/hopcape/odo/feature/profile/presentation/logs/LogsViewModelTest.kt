package com.hopcape.odo.feature.profile.presentation.logs

import com.hopcape.logging.api.LogFileHandle
import com.hopcape.logging.api.LogFileStats
import com.hopcape.logging.api.LogFileStore
import com.hopcape.logging.api.LogLevel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class LogsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun line(ts: Long = 0L, level: String = "INFO", tag: String = "Sync", event: String) =
        """{"ts":$ts,"level":"$level","tag":"$tag","event":"$event"}"""

    @Test
    fun noStore_isUnavailable_andStartPollingIsANoOp() {
        val viewModel = LogsViewModel(store = null)

        viewModel.startPolling()

        assertFalse(viewModel.state.value.available)
        assertTrue(viewModel.state.value.visibleEntries.isEmpty())
        viewModel.stopPolling()
    }

    @Test
    fun startPolling_loadsWhateverIsAlreadyInTheActiveFile() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (line(event = "one") + "\n").encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)

        viewModel.startPolling()

        assertTrue(viewModel.state.value.available)
        assertEquals(listOf("one"), viewModel.state.value.visibleEntries.map { it.message })
        viewModel.stopPolling()
    }

    @Test
    fun aLaterPoll_appendsOnlyTheNewTail_ratherThanReparsingFromTheStart() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (line(event = "one") + "\n").encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)
        viewModel.startPolling()
        viewModel.stopPolling()

        store.activeBytes = (line(event = "one") + "\n" + line(event = "two") + "\n").encodeToByteArray()
        viewModel.startPolling()
        viewModel.stopPolling()

        assertEquals(listOf("one", "two"), viewModel.state.value.visibleEntries.map { it.message })
    }

    @Test
    fun anIncompleteTrailingLine_isNotParsedUntilItsNewlineArrives() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (line(event = "one") + "\n").encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)
        viewModel.startPolling()
        viewModel.stopPolling()

        // The writer has only gotten partway through the next line.
        store.activeBytes = (line(event = "one") + "\n" + """{"ts":1,"level":"INFO","tag":"X""").encodeToByteArray()
        viewModel.startPolling()
        viewModel.stopPolling()
        assertEquals(listOf("one"), viewModel.state.value.visibleEntries.map { it.message })

        // Next tick, the line finished writing.
        store.activeBytes = (line(event = "one") + "\n" + line(event = "two") + "\n").encodeToByteArray()
        viewModel.startPolling()
        viewModel.stopPolling()
        assertEquals(listOf("one", "two"), viewModel.state.value.visibleEntries.map { it.message })
    }

    @Test
    fun aRotationToANewActiveFile_startsFreshRatherThanKeepingTheOldLines() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (line(event = "before rotation") + "\n").encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)
        viewModel.startPolling()
        viewModel.stopPolling()

        store.activeName = "b.log.active"
        store.activeBytes = (line(event = "after rotation") + "\n").encodeToByteArray()
        viewModel.startPolling()
        viewModel.stopPolling()

        assertEquals(listOf("after rotation"), viewModel.state.value.visibleEntries.map { it.message })
    }

    @Test
    fun searchFiltersByMessage_caseInsensitively() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (line(event = "Sync started") + "\n" + line(event = "Upload finished") + "\n").encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)
        viewModel.startPolling()

        viewModel.onSearchChanged("sync")

        assertEquals(listOf("Sync started"), viewModel.state.value.visibleEntries.map { it.message })
        viewModel.stopPolling()
    }

    @Test
    fun levelFilter_isAnOrAcrossSelectedLevels() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (
                line(level = "INFO", event = "info line") + "\n" +
                    line(level = "WARN", event = "warn line") + "\n" +
                    line(level = "ERROR", event = "error line") + "\n"
                ).encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)
        viewModel.startPolling()

        viewModel.onLevelToggled(LogLevel.WARN)
        viewModel.onLevelToggled(LogLevel.ERROR)

        assertEquals(
            setOf("warn line", "error line"),
            viewModel.state.value.visibleEntries.map { it.message }.toSet(),
        )
        viewModel.stopPolling()
    }

    @Test
    fun tagFilter_togglingOffRemovesItFromTheSelection() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (line(tag = "Sync", event = "a") + "\n" + line(tag = "Refuel", event = "b") + "\n").encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)
        viewModel.startPolling()

        viewModel.onTagToggled("Sync")
        assertEquals(listOf("a"), viewModel.state.value.visibleEntries.map { it.message })

        viewModel.onTagToggled("Sync")
        assertEquals(setOf("a", "b"), viewModel.state.value.visibleEntries.map { it.message }.toSet())
        viewModel.stopPolling()
    }

    @Test
    fun allTags_reflectsEveryLoadedLine_notJustTheFilteredOnes() {
        val store = FakeLogFileStore(
            activeName = "a.log.active",
            activeBytes = (line(tag = "Sync", event = "a") + "\n" + line(tag = "Refuel", event = "b") + "\n").encodeToByteArray(),
        )
        val viewModel = LogsViewModel(store)
        viewModel.startPolling()

        viewModel.onTagToggled("Sync")

        assertEquals(listOf("Refuel", "Sync"), viewModel.state.value.allTags)
        viewModel.stopPolling()
    }

    private class FakeLogFileStore(
        var activeName: String?,
        var activeBytes: ByteArray,
    ) : LogFileStore {
        override fun appendToActive(lines: List<String>) = Unit
        override fun activeFileName(): String? = activeName
        override fun sealActive(stats: LogFileStats): LogFileHandle? = null
        override fun sealOrphans(): List<LogFileHandle> = emptyList()
        override fun listSealed(): List<LogFileHandle> = emptyList()
        override fun read(name: String): ByteArray? = if (name == activeName) activeBytes else null
        override fun delete(name: String) = Unit
        override fun totalBytes(): Long = 0L
    }
}
