package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.AnalyticsSink
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.model.AnalyticsEvent

// ─────────────────────────────────────────────────────────────
// SinkDestination — Adapter from the public AnalyticsSink port to
// the internal AnalyticsDestination pipeline shape, so an external
// sink is wrapped in SafeDestination exactly like a built-in
// destination. Keeps AnalyticsEvent internal: a sink only ever sees
// the resolved name/properties/timestamp, never the event model.
// ─────────────────────────────────────────────────────────────
internal class SinkDestination(
    private val sink: AnalyticsSink,
) : AnalyticsDestination {

    override val name: String = sink.name

    override fun identify(traits: UserTraits) = sink.identify(traits)

    override fun track(event: AnalyticsEvent) =
        sink.track(event.name, event.properties, event.timestampMs)

    override fun flush() = sink.flush()
}
