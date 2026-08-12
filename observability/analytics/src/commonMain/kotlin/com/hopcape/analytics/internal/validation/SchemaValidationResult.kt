package com.hopcape.analytics.internal.validation

// ─────────────────────────────────────────────────────────────
// SchemaValidationResult — the outcome of checking an event's
// properties against its registered schema. `Unregistered` is a
// first-class state (distinct from `Invalid`) so the tracker can
// apply a different policy to "unknown event" vs "known event,
// wrong shape": production lets unknowns through, while a wrong
// shape is always a violation.
// ─────────────────────────────────────────────────────────────
internal sealed interface SchemaValidationResult {
    data object Valid : SchemaValidationResult
    data object Unregistered : SchemaValidationResult
    data class Invalid(val reasons: List<String>) : SchemaValidationResult
}
