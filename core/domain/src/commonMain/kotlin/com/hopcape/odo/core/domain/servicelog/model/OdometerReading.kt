package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate

/**
 * One known odometer reading for a car, at the date it was taken.
 *
 * A car's readings come from two places: every [ServiceLogEntry] carries one (odometer is
 * mandatory on every log — it is Odo's core number), and onboarding records a baseline when
 * the car is added. [logId] distinguishes them — `null` is the onboarding baseline, which is
 * why it is nullable rather than the entry id being enough.
 *
 * Together they form the timeline
 * [OdometerTimeline][com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline]
 * validates a new or edited reading against. Kept as its own model rather than passing raw
 * `Int` km around, because the ordering rule is *date*-relative: a reading means nothing
 * without the day it was taken.
 */
data class OdometerReading(
    val logId: ServiceLogId?,
    val date: LocalDate,
    val odometer: Distance,
)
