package com.hopcape.analytics.api

// ─────────────────────────────────────────────────────────────
// AnalyticsTracker — the minimal public contract (ISP). This is
// what ViewModels / repositories / use cases depend on and inject.
// They never see destinations, the dispatch queue, dedup, or the
// event schema machinery — all of that is `internal` to the module.
// ─────────────────────────────────────────────────────────────
interface AnalyticsTracker {

    /** Associates subsequent events with a user identity (forwarded to every destination). */
    fun identify(traits: UserTraits)

    /**
     * Records a single event. [properties] are validated against the registered
     * [EventSchema] (when one exists) before the event is queued for delivery.
     */
    fun track(eventName: String, properties: Map<String, Any?> = emptyMap())

    /** Updates the consent gate. Nothing is tracked until consent is [ConsentStatus.GRANTED]. */
    fun setConsent(status: ConsentStatus)

    /** Forces the dispatch queue to attempt delivery now, rather than on the next interval. */
    fun flush()
}
