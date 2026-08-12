package com.hopcape.performance.internal.sampling

import com.hopcape.performance.testSpan
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveSamplerTest {

    private fun sampler(
        sampleRate: Double = 0.0,
        slowThresholdMs: Long = 2_000,
        keepAll: Boolean = false,
        random: () -> Double = { 0.99 },
    ) = AdaptiveSampler(sampleRate, slowThresholdMs, keepAll, random)

    @Test
    fun keepAll_keepsEverything_evenFastSuccessfulSpans() {
        val s = sampler(sampleRate = 0.0, keepAll = true, random = { 0.99 })
        assertTrue(s.shouldSample(testSpan("fast", "1", durationMs = 5)))
    }

    @Test
    fun erroredSpan_isAlwaysKept_evenAtZeroRate() {
        val s = sampler(sampleRate = 0.0, random = { 0.99 })
        val errored = testSpan("checkout", "1", attributes = mapOf("error" to "OUT_OF_STOCK"), durationMs = 10)
        assertTrue(s.shouldSample(errored), "an errored span must never be sampled away")
    }

    @Test
    fun slowSpan_isAlwaysKept_evenAtZeroRate() {
        val s = sampler(sampleRate = 0.0, slowThresholdMs = 2_000, random = { 0.99 })
        assertTrue(s.shouldSample(testSpan("slow", "1", durationMs = 2_500)))
        assertTrue(s.shouldSample(testSpan("exactly_at_threshold", "2", durationMs = 2_000)))
    }

    @Test
    fun ordinaryFastSpan_obeysTheRate() {
        // random() < rate keeps; otherwise drop.
        assertTrue(sampler(sampleRate = 0.5, random = { 0.4 }).shouldSample(testSpan("f", "1", durationMs = 10)))
        assertFalse(sampler(sampleRate = 0.5, random = { 0.6 }).shouldSample(testSpan("f", "2", durationMs = 10)))
    }

    @Test
    fun rateBoundaries_shortCircuit() {
        assertTrue(sampler(sampleRate = 1.0, random = { 0.99 }).shouldSample(testSpan("f", "1", durationMs = 10)))
        assertFalse(sampler(sampleRate = 0.0, random = { 0.0 }).shouldSample(testSpan("f", "2", durationMs = 10)))
    }
}
