package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.MotionKind
import com.hopcape.odo.core.triptracker.model.MotionSignal

/**
 * Debounces the raw activity-recognizer stream into a settled [MotionKind] — it takes
 * [TripTrackerConfig.motionDebounceConsecutiveReadings] consecutive readings at or above
 * [TripTrackerConfig.motionDebounceMinConfidence] agreeing on the same kind before the
 * state machine acts on it. v3 never actually implemented this rule.
 */
internal class MotionDebouncer(private val config: TripTrackerConfig) {

    private var candidateKind: MotionKind? = null
    private var candidateStreak: Int = 0
    private var settled: MotionKind? = null

    /**
     * Feeds one reading. Returns the newly-settled kind, or `null` if nothing changed —
     * including a single low-confidence reading, which is ignored rather than breaking a
     * streak of good ones.
     */
    fun accept(signal: MotionSignal): MotionKind? {
        if (signal.confidence < config.motionDebounceMinConfidence) return null

        if (signal.kind == candidateKind) {
            candidateStreak++
        } else {
            candidateKind = signal.kind
            candidateStreak = 1
        }

        if (candidateStreak >= config.motionDebounceConsecutiveReadings && settled != candidateKind) {
            settled = candidateKind
            return settled
        }
        return null
    }

    /** The last settled kind, or `null` if nothing has settled yet. */
    fun current(): MotionKind? = settled
}
