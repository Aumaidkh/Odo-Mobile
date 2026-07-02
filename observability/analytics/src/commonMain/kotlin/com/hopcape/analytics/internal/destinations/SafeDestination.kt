package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.model.AnalyticsEvent

// ─────────────────────────────────────────────────────────────
// SafeDestination — Decorator, the same fail-safe guarantee as the
// logger's SafeSink. A vendor SDK throwing (network down, malformed
// payload, SDK not initialized) must NEVER crash the host app or
// abort delivery to the *other* destinations.
// ─────────────────────────────────────────────────────────────
internal class SafeDestination(
    private val delegate: AnalyticsDestination,
    private val onInternalError: (destinationName: String, error: Throwable) -> Unit,
) : AnalyticsDestination {

    override val name: String = delegate.name

    override fun identify(traits: UserTraits) {
        runCatching { delegate.identify(traits) }.onFailure { onInternalError(name, it) }
    }

    override fun track(event: AnalyticsEvent) {
        runCatching { delegate.track(event) }.onFailure { onInternalError(name, it) }
    }

    override fun flush() {
        runCatching { delegate.flush() }.onFailure { onInternalError(name, it) }
    }
}
