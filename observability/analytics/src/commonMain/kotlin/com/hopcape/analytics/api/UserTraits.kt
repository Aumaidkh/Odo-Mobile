package com.hopcape.analytics.api

// ─────────────────────────────────────────────────────────────
// UserTraits — identity payload for identify() calls. Kept
// separate from an event (SRP): identity resolution and event
// tracking are different concerns with different delivery
// guarantees (identify is rarer and more critical to land).
// ─────────────────────────────────────────────────────────────
data class UserTraits(
    val userId: String,
    val traits: Map<String, Any?> = emptyMap(),
)
