package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.model.AnalyticsEvent

// ─────────────────────────────────────────────────────────────
// AnalyticsDestination — Strategy, one per vendor SDK. The tracker
// never imports PostHog / Firebase SDKs directly (DIP); it only
// knows this interface. Swapping or adding a vendor = a new class
// here, zero changes elsewhere (OCP).
// ─────────────────────────────────────────────────────────────
internal interface AnalyticsDestination {
    val name: String
    fun identify(traits: UserTraits)
    fun track(event: AnalyticsEvent)
    fun flush()
}
