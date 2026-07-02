package com.hopcape.performance.internal.sampling

import com.hopcape.performance.internal.model.CompletedSpan

// ─────────────────────────────────────────────────────────────
// Sampler — the keep/drop decision for a finished span (DIP). Kept
// behind an interface so the tracer depends on the policy, not a
// concrete rule, and tests can inject a deterministic sampler.
// ─────────────────────────────────────────────────────────────
internal interface Sampler {
    /** True if [span] should be kept and exported; false to drop it. */
    fun shouldSample(span: CompletedSpan): Boolean
}
