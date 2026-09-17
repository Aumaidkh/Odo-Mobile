package com.hopcape.analytics.api

// ─────────────────────────────────────────────────────────────
// AnalyticsSink — the port an outside module implements to add a
// vendor destination (e.g. Firebase) without this module depending
// on that vendor's SDK. Public and deliberately smaller than the
// internal AnalyticsDestination: a sink never sees the internal
// AnalyticsEvent/GlobalContext types, only the resolved values it
// needs to forward.
//
// A sink registered via AnalyticsConfig.destinations is wrapped in
// SafeDestination like every built-in destination, so a throwing
// sink can't crash the host or block delivery to the others.
// ─────────────────────────────────────────────────────────────
interface AnalyticsSink {
    val name: String
    fun identify(traits: UserTraits)

    /** Drops the identity. Defaulted, so an existing sink needs no change. */
    fun forget() {}

    /** Returns whether the vendor SDK accepted the event — decides whether it is retried. */
    fun track(eventName: String, properties: Map<String, Any?>, timestampMs: Long): Boolean
    fun flush()
}
