package com.hopcape.odo.core.domain.servicelog.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OdometerAnomaliesTest {

    private fun reading(id: String?, month: Int, day: Int, km: Int) = OdometerReading(
        logId = id?.let(::ServiceLogId),
        date = LocalDate(2026, month, day),
        odometer = Distance.of(km).getOrElse { error("test fixture km=$km") },
    )

    @Test
    fun aTimelineThatOnlyCountsUp_hasNoAnomalies() {
        val readings = listOf(
            reading(null, 1, 10, 30_000),
            reading("log-1", 4, 2, 36_400),
            reading("log-2", 7, 18, 41_000),
        )

        assertEquals(emptyList(), OdometerTimeline.anomalies(readings))
    }

    @Test
    fun aReadingBelowAnEarlierOne_isReported() {
        val earlier = reading("log-1", 4, 2, 36_400)
        val later = reading("log-2", 7, 18, 31_000)

        val found = OdometerTimeline.anomalies(listOf(later, earlier))

        assertEquals(1, found.size)
        assertEquals(earlier, found.single().earlier)
        assertEquals(later, found.single().later)
        assertEquals(5_400, found.single().droppedByKm)
    }

    @Test
    fun readingsOnTheSameDay_doNotConstrainEachOther() {
        // Two garages in one day, logged in either order: a service date carries no time,
        // so there is no knowable order to violate.
        val readings = listOf(
            reading("log-1", 5, 6, 38_000),
            reading("log-2", 5, 6, 37_800),
        )

        assertTrue(OdometerTimeline.anomalies(readings).isEmpty())
    }

    @Test
    fun aReadingIsMeasuredAgainstTheHighestBeforeIt_notTheNearest() {
        // 36,400 in April is the peak; May's 35,000 is already an anomaly, and June's
        // 35,500 is still below that peak even though it is above May.
        val readings = listOf(
            reading("log-1", 4, 2, 36_400),
            reading("log-2", 5, 9, 35_000),
            reading("log-3", 6, 20, 35_500),
        )

        val found = OdometerTimeline.anomalies(readings)

        assertEquals(2, found.size)
        assertEquals(listOf(1_400, 900), found.map { it.droppedByKm })
    }

    @Test
    fun aRecoveredTimeline_reportsOnlyTheDip() {
        val readings = listOf(
            reading(null, 1, 10, 30_000),
            reading("log-1", 3, 3, 28_000),
            reading("log-2", 9, 1, 44_000),
        )

        val found = OdometerTimeline.anomalies(readings)

        assertEquals(1, found.size)
        assertEquals(2_000, found.single().droppedByKm)
    }

    @Test
    fun theOrderTheReadingsArrivedIn_doesNotMatter() {
        val chronological = listOf(
            reading(null, 1, 10, 30_000),
            reading("log-1", 3, 3, 28_000),
            reading("log-2", 9, 1, 44_000),
        )

        assertEquals(
            OdometerTimeline.anomalies(chronological),
            OdometerTimeline.anomalies(chronological.reversed()),
        )
    }

    @Test
    fun anEmptyTimeline_hasNothingToReport() {
        assertEquals(emptyList(), OdometerTimeline.anomalies(emptyList()))
    }
}
