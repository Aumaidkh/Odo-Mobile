package com.hopcape.odo.core.triptracker.model

import kotlin.time.Instant

/** One motion-activity reading from the platform's activity recognizer. */
internal data class MotionSignal(
    val kind: MotionKind,
    /** 0..100, as reported by the platform's activity recognizer. */
    val confidence: Int,
    val at: Instant,
)

/** The activity kinds the state machine and debouncer care about. */
internal enum class MotionKind { IN_VEHICLE, WALKING, STILL, ON_FOOT, UNKNOWN }
