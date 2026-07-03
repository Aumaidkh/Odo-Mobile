package com.hopcape.odo.core.domain.servicelog.model

import kotlin.jvm.JvmInline

/**
 * A 0–100 record/health score. A rule-**derived** value (not user input), so [of]
 * coerces into range rather than failing. A `value class` so it can never be mixed up
 * with a raw `Int` count, percentage, or the record's sample size.
 */
@JvmInline
value class RecordScore private constructor(val value: Int) {
    companion object {
        val RANGE = 0..100
        val ZERO = RecordScore(0)

        fun of(value: Int): RecordScore = RecordScore(value.coerceIn(RANGE))
    }
}
