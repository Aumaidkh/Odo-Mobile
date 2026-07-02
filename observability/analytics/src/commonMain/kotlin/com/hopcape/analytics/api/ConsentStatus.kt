package com.hopcape.analytics.api

// ─────────────────────────────────────────────────────────────
// ConsentStatus — GDPR / India DPDP compliance gate. Nothing is
// tracked while the status isn't GRANTED (fail-closed, not
// fail-open). Part of the public contract because callers set it.
// ─────────────────────────────────────────────────────────────
enum class ConsentStatus {
    /** No decision recorded yet — treated as "do not track". */
    UNKNOWN,

    /** User opted in — events flow to destinations. */
    GRANTED,

    /** User opted out — events are dropped at the gate. */
    DENIED,
}
