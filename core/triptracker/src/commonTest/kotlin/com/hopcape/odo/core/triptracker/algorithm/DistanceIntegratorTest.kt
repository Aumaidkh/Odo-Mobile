package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceIntegratorTest {

    private val config = TripTrackerConfig()
    private lateinit var integrator: DistanceIntegrator

    @BeforeTest
    fun setUp() {
        integrator = DistanceIntegrator(config)
    }

    @Test
    fun straightHighway_dopplerSumMatchesTruthWithinHalfPercent() {
        val speed = 25f // ~90 km/h
        val samples = TraceBuilder.straightLine(
            from = 19.00 to 72.80,
            to = 19.50 to 73.30,
            speedMps = speed,
            accuracyM = 10f,
            samples = 100,
        )
        val total = samples.sumOf { integrator.accept(it).addedMeters }
        val truth = speed * 100 // 100 one-second steps at a constant speed
        assertTrue(abs(total - truth) <= truth * 0.005, "total=$total truth=$truth")
    }

    @Test
    fun cityGridWithTurn_chordSumMatchesTruthWithinHalfPercent() {
        val stepDeg = 0.0003 // ~33 m per tick, comfortably clear of the combined-uncertainty gate
        val accuracyM = 5f
        val samples = buildList {
            for (tick in 0..20) {
                add(TraceBuilder.sample(tick = tick, lat = 19.0 + stepDeg * tick, lon = 72.80, accuracyM = accuracyM))
            }
            for (i in 1..20) {
                add(
                    TraceBuilder.sample(
                        tick = 20 + i,
                        lat = 19.0 + stepDeg * 20,
                        lon = 72.80 + stepDeg * i,
                        accuracyM = accuracyM,
                    ),
                )
            }
        }
        val total = samples.sumOf { integrator.accept(it).addedMeters }
        val truth = samples.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }.sum()
        val relativeError = abs(total - truth) / truth
        assertTrue(relativeError < 0.005, "relativeError=$relativeError total=$total truth=$truth")
    }

    @Test
    fun tunnelDropout_closesGapExactlyOnce() {
        val good1 = TraceBuilder.straightLine(
            from = 19.000 to 72.80,
            to = 19.010 to 72.80,
            speedMps = 20f,
            accuracyM = 10f,
            samples = 5,
        )
        val bad = (0..4).map { i ->
            TraceBuilder.sample(tick = 100 + i, lat = 19.011, lon = 72.80, accuracyM = 500f)
        }
        val good2 = (0..4).map { i ->
            TraceBuilder.sample(
                tick = 200 + i,
                lat = 19.02 + i * stepDeg,
                lon = 72.80,
                accuracyM = 10f,
                speedMps = 20f,
            )
        }

        val results = (good1 + bad + good2).map { integrator.accept(it) }

        assertEquals(1, results.count { it.gapOpened })
        assertEquals(1, results.count { it.gapClosed != null })

        val gapOpenIndex = results.indexOfFirst { it.gapOpened }
        val gapCloseIndex = results.indexOfFirst { it.gapClosed != null }
        assertTrue(gapOpenIndex in results.indices && gapCloseIndex > gapOpenIndex)
        for (i in gapOpenIndex..gapCloseIndex) {
            assertEquals(0L, results[i].addedMeters, "sample $i added distance while a gap was open")
        }
    }

    @Test
    fun noisyStationaryCluster_addsNearZero() {
        val samples = TraceBuilder.stationaryCluster(at = 19.0 to 72.80, accuracyM = 20f, count = 20, jitterMeters = 3.0)
        val total = samples.sumOf { integrator.accept(it).addedMeters }
        assertEquals(0L, total)
    }

    @Test
    fun walkingSpeedJitter_rejectedByAccelerationGate() {
        // Position never moves; speed alternates 0/8 m/s, an 8 m/s^2 swing well past the
        // 4 m/s^2 plausibility gate. Without that gate this would add ~76 m of phantom
        // distance; the chord path (which does see the stationary position) confirms zero.
        val samples = (0..19).map { tick ->
            val speed = if (tick % 2 == 0) 0f else 8f
            TraceBuilder.sample(tick = tick, lat = 19.0, lon = 72.80, accuracyM = 10f, speedMps = speed)
        }
        val total = samples.sumOf { integrator.accept(it).addedMeters }
        assertEquals(0L, total)
    }

    private companion object {
        const val stepDeg = 0.0003
    }
}
