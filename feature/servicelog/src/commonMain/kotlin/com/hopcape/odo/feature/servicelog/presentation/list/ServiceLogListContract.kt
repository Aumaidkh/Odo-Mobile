package com.hopcape.odo.feature.servicelog.presentation.list

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId

/** Ledger filter chips (mockup 1a: All / Verified / Flagged). */
internal enum class ServiceLogFilter { ALL, VERIFIED, FLAGGED }

/** User intents on the service-log list. */
internal sealed interface ServiceLogListEvent {
    data class FilterChanged(val filter: ServiceLogFilter) : ServiceLogListEvent
    data class LogClicked(val id: ServiceLogId) : ServiceLogListEvent
    data object AddClicked : ServiceLogListEvent
    data object ScanClicked : ServiceLogListEvent // coming soon (no-op until M2)
    data object Back : ServiceLogListEvent
}

/** One-shot navigation effects (data, not actions — the route host performs them). */
internal sealed interface ServiceLogListEffect {
    data class OpenDetail(val id: ServiceLogId) : ServiceLogListEffect
    data object OpenAdd : ServiceLogListEffect
    data object Back : ServiceLogListEffect
}
