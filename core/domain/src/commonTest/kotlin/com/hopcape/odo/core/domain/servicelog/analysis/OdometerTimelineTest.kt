package com.hopcape.odo.core.domain.servicelog.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OdometerTimelineTest {

    private fun reading(id: String?, year: Int, month: Int, day: Int, km: Int) = OdometerReading(
        logId = id?.let(::ServiceLogId),
        date = LocalDate(year, month, day),
        odometer = Distance.of(km).getOrElse { error("test fixture km=$km") },
    )

    /** The car's onboarding baseline: 45,000 km recorded on 1 Jul 2026. */
    private val baseline = reading(id = null, year = 2026, month = 7, day = 1, km = 45_000)

    @Test
    fun readingAfterTheBaseline_isAccepted() {
        val candidate = reading("log-1", 2026, 7, 20, 46_500)

        val result = OdometerTimeline.validate(candidate, listOf(baseline))

        assertTrue(result.isRight(), "expected Right but was $result")
    }

    @Test
    fun backdatedHistoryBelowTheBaseline_isAccepted() {
        // The resale-proof flow: a car onboarded at 45,000 km today, with its 2024
        // service (30,000 km) logged afterwards.
        val candidate = reading("log-1", 2024, 3, 12, 30_000)

        val result = OdometerTimeline.validate(candidate, listOf(baseline))

        assertTrue(result.isRight(), "backdated history must be loggable: $result")
    }

    @Test
    fun backdatedReadingAboveALaterEntry_isRejected() {
        // 60,000 km in March cannot be true when the car read 45,000 km in July.
        val candidate = reading("log-1", 2026, 3, 12, 60_000)

        val error = OdometerTimeline.validate(candidate, listOf(baseline)).leftOrNull()

        val ahead = assertIs<DomainError.OdometerAheadOfLaterEntry>(error)
        assertEquals(45_000, ahead.nextKm)
        assertEquals(60_000, ahead.attemptedKm)
    }

    @Test
    fun readingBelowAnEarlierEntry_isRejected() {
        val candidate = reading("log-1", 2026, 7, 20, 40_000)

        val error = OdometerTimeline.validate(candidate, listOf(baseline)).leftOrNull()

        val regression = assertIs<DomainError.OdometerRegression>(error)
        assertEquals(45_000, regression.previousKm)
        assertEquals(40_000, regression.attemptedKm)
    }

    @Test
    fun readingBetweenTwoEntries_isAccepted() {
        val later = reading("log-2", 2026, 12, 1, 55_000)
        val candidate = reading("log-3", 2026, 9, 1, 50_000)

        val result = OdometerTimeline.validate(candidate, listOf(baseline, later))

        assertTrue(result.isRight(), "a reading inside its neighbours must pass: $result")
    }

    @Test
    fun editingAnEntryDownwards_ignoresItsOwnStoredReading() {
        // The typo fix: log-2 was entered as 55,000 and is being corrected to 50,000.
        // Compared against its own stored value this looks like a regression; excluding
        // itself, 50,000 sits comfortably above the 45,000 baseline.
        val stored = reading("log-2", 2026, 12, 1, 55_000)
        val corrected = reading("log-2", 2026, 12, 1, 50_000)

        val result = OdometerTimeline.validate(corrected, listOf(baseline, stored))

        assertTrue(result.isRight(), "an entry must not be checked against itself: $result")
    }

    @Test
    fun aSameDayReadingBelowAnExistingOne_isRejected() {
        // The reported bug: a service logged at 46,200 km today must not let a second
        // service be logged at 46,000 km today — the odometer only counts up.
        val logged = reading("log-1", 2026, 7, 20, 46_200)
        val candidate = reading("log-2", 2026, 7, 20, 46_000)

        val error = OdometerTimeline.validate(candidate, listOf(baseline, logged)).leftOrNull()

        val regression = assertIs<DomainError.OdometerRegression>(error)
        assertEquals(46_200, regression.previousKm)
        assertEquals(46_000, regression.attemptedKm)
    }

    @Test
    fun aSameDayReadingAboveAnExistingOne_isAccepted() {
        // Two garages in one day, logged in the order they happened.
        val morning = reading("log-1", 2026, 7, 20, 46_000)
        val second = reading("log-2", 2026, 7, 20, 46_200)

        assertTrue(OdometerTimeline.validate(second, listOf(baseline, morning)).isRight())
    }

    @Test
    fun theCarsOwnReadingRewrittenOnItsOwnDay_ignoresItsStoredValue() {
        // The garage typo fix: the baseline was written down today as 450,000 and is being
        // corrected to 45,000. Its same-day stored value is the mistake, not a constraint.
        val stored = reading(null, 2026, 7, 1, 450_000)
        val corrected = reading(null, 2026, 7, 1, 45_000)

        assertTrue(OdometerTimeline.validate(corrected, listOf(stored)).isRight())
    }

    @Test
    fun theCarsOwnReadingRewrittenLater_isStillBoundByItsOldValue() {
        // The baseline dated 1 Jul was a real observation; a lower reading on a later day
        // contradicts it and is refused.
        val candidate = reading(null, 2026, 7, 20, 40_000)

        val error = OdometerTimeline.validate(candidate, listOf(baseline)).leftOrNull()

        assertIs<DomainError.OdometerRegression>(error)
    }

    @Test
    fun noKnownReadings_acceptsAnything() {
        val result = OdometerTimeline.validate(reading("log-1", 2026, 7, 20, 46_000), emptyList())

        assertTrue(result.isRight(), "with nothing to compare against there is no violation: $result")
    }

    @Test
    fun equalToANeighbour_isAccepted() {
        // Two services can share a reading (a follow-up job with no driving in between).
        val candidate = reading("log-1", 2026, 7, 20, 45_000)

        assertTrue(OdometerTimeline.validate(candidate, listOf(baseline)).isRight())
    }

    @Test
    fun theTightestNeighbourIsTheOneEnforced() {
        // The lower bound is the highest reading before the candidate — not the oldest one.
        val old = reading("log-1", 2024, 1, 1, 20_000)
        val recent = reading("log-2", 2026, 6, 1, 44_000)
        val candidate = reading("log-3", 2026, 6, 15, 43_000)

        val error = OdometerTimeline.validate(candidate, listOf(old, recent, baseline)).leftOrNull()

        val regression = assertIs<DomainError.OdometerRegression>(error)
        assertEquals(44_000, regression.previousKm)
    }
}
