package com.hopcape.odo.feature.profile.presentation.logs

import com.hopcape.logging.api.LogLevel

/**
 * The Logs screen's state. [allTags] is derived from every line loaded so far, not from
 * [visibleEntries] — toggling a tag off must not make that tag disappear from the chip row.
 */
internal data class LogsUiState(
    /** `false` on a build with no [com.hopcape.logging.api.LogFileStore] bound (iOS today) —
     *  the screen shows [com.hopcape.odo.feature.profile.resources.Res.string.pf_logs_unavailable]
     *  instead of an empty list, which would otherwise look identical to "nothing logged yet". */
    val available: Boolean = true,
    val visibleEntries: List<LogEntry> = emptyList(),
    val allTags: List<String> = emptyList(),
    val searchText: String = "",
    val selectedLevels: Set<LogLevel> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
) {
    val hasAnyEntries: Boolean get() = allTags.isNotEmpty()
}
