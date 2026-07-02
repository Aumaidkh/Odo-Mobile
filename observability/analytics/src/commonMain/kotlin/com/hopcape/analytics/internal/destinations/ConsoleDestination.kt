package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.model.AnalyticsEvent

// ─────────────────────────────────────────────────────────────
// ConsoleDestination — a debug-only destination that echoes events
// to a sink (println by default). Added by the factory when
// AnalyticsConfig.isDebug is true, so engineers can see the exact
// event/properties/sequence flowing through the pipeline without a
// live vendor SDK. The sink is injectable, which keeps it testable.
// ─────────────────────────────────────────────────────────────
internal class ConsoleDestination(
    private val sink: (String) -> Unit = ::println,
) : AnalyticsDestination {

    override val name: String = "console"

    override fun identify(traits: UserTraits) {
        sink("[analytics] identify userId=${traits.userId} traits=${traits.traits}")
    }

    override fun track(event: AnalyticsEvent) {
        sink("[analytics] track ${event.name} seq=${event.sequenceNumber} props=${event.properties}")
    }

    override fun flush() = Unit
}
