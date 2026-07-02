package com.hopcape.performance.internal.sampling

import com.hopcape.performance.internal.model.CompletedSpan
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────
// AdaptiveSampler — head-based sampling that adapts to how
// *interesting* a span is rather than dropping blindly by rate:
//
//   1. Debug builds keep everything (keepAll) — you want full
//      fidelity while developing.
//   2. Errored spans are ALWAYS kept — a failure you sampled away
//      is a failure you can't debug. (This is why setting an
//      `error` attribute guarantees the span reaches the dashboard.)
//   3. Slow spans (>= slowThresholdMs) are ALWAYS kept — the latency
//      tail is exactly what APM exists to surface.
//   4. Everything else — the fast, successful majority — is kept at
//      [sampleRate], so the healthy common case doesn't flood the
//      backend or the user's data budget.
//
// [random] is injectable so tests are deterministic; production uses
// Random.nextDouble() in [0.0, 1.0).
// ─────────────────────────────────────────────────────────────
internal class AdaptiveSampler(
    private val sampleRate: Double,
    private val slowThresholdMs: Long,
    private val keepAll: Boolean,
    private val random: () -> Double = { Random.nextDouble() },
) : Sampler {

    override fun shouldSample(span: CompletedSpan): Boolean = when {
        keepAll -> true
        span.isError -> true
        span.durationMs >= slowThresholdMs -> true
        sampleRate >= 1.0 -> true
        sampleRate <= 0.0 -> false
        else -> random() < sampleRate
    }
}
