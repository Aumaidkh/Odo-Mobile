package com.hopcape.odo.feature.profile.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.logging.api.LogFileStore
import com.hopcape.logging.api.LogLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Logs screen's state and its poll loop.
 *
 * [store] is nullable because no platform binds [LogFileStore] on iOS today — the screen
 * degrades to [LogsUiState.available] `false` there rather than crashing, the same
 * defensive shape [com.hopcape.odo.feature.profile.presentation.ConfigOverridesViewModel]
 * already uses for its nullable [com.hopcape.odo.core.config.LocalConfigOverrides].
 *
 * Reads by polling, not by observing a stream from the sink chain: [AsyncSink]'s writer
 * already flushes the active file to disk on every append, so this only ever needs to
 * notice the file grew — no new consumer on the frozen `Logger`/`LogSink` surface.
 * [startPolling]/[stopPolling] are called from the route as the screen enters/leaves
 * composition, so nothing polls while the screen isn't visible.
 */
internal class LogsViewModel(
    private val store: LogFileStore?,
) : ViewModel() {

    private val rawEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    private val searchText = MutableStateFlow("")
    private val selectedLevels = MutableStateFlow<Set<LogLevel>>(emptySet())
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<LogsUiState> = combine(
        rawEntries, searchText, selectedLevels, selectedTags,
    ) { entries, search, levels, tags ->
        LogsUiState(
            available = store != null,
            visibleEntries = entries.filter { entry ->
                (levels.isEmpty() || entry.level in levels) &&
                    (tags.isEmpty() || entry.tag in tags) &&
                    (search.isBlank() || entry.message.contains(search, ignoreCase = true))
            },
            allTags = entries.map { it.tag }.distinct().sorted(),
            searchText = search,
            selectedLevels = levels,
            selectedTags = tags,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LogsUiState(available = store != null))

    private var pollingJob: Job? = null
    private var activeFileName: String? = null
    private var bytesRead = 0

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                readNewLines()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun onSearchChanged(text: String) {
        searchText.value = text
    }

    fun onLevelToggled(level: LogLevel) {
        selectedLevels.update { if (level in it) it - level else it + level }
    }

    fun onTagToggled(tag: String) {
        selectedTags.update { if (tag in it) it - tag else it + tag }
    }

    /**
     * Reads whatever's new in the active file since the last tick — never the whole file —
     * and only up through the last complete line, so a write the writer coroutine hasn't
     * finished yet is picked up whole next tick instead of being parsed torn.
     */
    private fun readNewLines() {
        val store = store ?: return
        val name = store.activeFileName() ?: return
        if (name != activeFileName) {
            // A rotation (or the first tick of this session) — start again from byte 0.
            activeFileName = name
            bytesRead = 0
            rawEntries.value = emptyList()
        }

        val bytes = store.read(name) ?: return
        if (bytes.size <= bytesRead) return

        val newBytes = bytes.copyOfRange(bytesRead, bytes.size)
        val lastNewline = newBytes.lastIndexOf(NEWLINE)
        if (lastNewline < 0) return
        val completeChunk = newBytes.copyOfRange(0, lastNewline + 1)
        bytesRead += completeChunk.size

        val newEntries = LogLineParser.parseLines(completeChunk.decodeToString())
        if (newEntries.isNotEmpty()) {
            rawEntries.update { (it + newEntries).takeLast(MAX_ENTRIES) }
        }
    }

    override fun onCleared() {
        stopPolling()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val MAX_ENTRIES = 2_000
        const val NEWLINE: Byte = '\n'.code.toByte()
    }
}
