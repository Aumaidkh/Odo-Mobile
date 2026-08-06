package com.hopcape.odo.core.domain.reminder.policy

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CadencePlannerTest {

    private val start = LocalDate(2026, 8, 6)

    private fun onDate(date: LocalDate) = ReminderOccurrence.OnDate(date)

    /* ---- Once ---- */

    @Test
    fun onceFiresOnItsDay() {
        assertEquals(
            onDate(start),
            CadencePlanner.nextOccurrence(ReminderCadence.Once, start, from = start),
        )
    }

    @Test
    fun onceStillAheadIsReturned() {
        assertEquals(
            onDate(start),
            CadencePlanner.nextOccurrence(ReminderCadence.Once, start, from = LocalDate(2026, 8, 1)),
        )
    }

    @Test
    fun oncePassedIsOver() {
        assertNull(
            CadencePlanner.nextOccurrence(ReminderCadence.Once, start, from = LocalDate(2026, 8, 7)),
        )
    }

    /* ---- EveryDays ---- */

    @Test
    fun everyDaysBeforeTheStartFiresOnTheStart() {
        assertEquals(
            onDate(start),
            CadencePlanner.nextOccurrence(
                ReminderCadence.EveryDays(15),
                start,
                from = LocalDate(2026, 8, 1),
            ),
        )
    }

    @Test
    fun everyDaysOnAnOccurrenceDayFiresThatDay() {
        assertEquals(
            onDate(LocalDate(2026, 8, 21)),
            CadencePlanner.nextOccurrence(
                ReminderCadence.EveryDays(15),
                start,
                from = LocalDate(2026, 8, 21),
            ),
        )
    }

    @Test
    fun everyDaysMidCycleFiresAtTheNextStep() {
        assertEquals(
            onDate(LocalDate(2026, 8, 21)),
            CadencePlanner.nextOccurrence(
                ReminderCadence.EveryDays(15),
                start,
                from = LocalDate(2026, 8, 10),
            ),
        )
    }

    @Test
    fun everyDaysSkipsMissedOccurrencesInsteadOfQueuingThem() {
        // Three cycles slept through — the answer is the next one ahead, not a backlog.
        assertEquals(
            onDate(LocalDate(2026, 10, 5)),
            CadencePlanner.nextOccurrence(
                ReminderCadence.EveryDays(15),
                start,
                from = LocalDate(2026, 10, 1),
            ),
        )
    }

    /* ---- Monthly ---- */

    @Test
    fun monthlyFiresTheSameDayNextMonth() {
        assertEquals(
            onDate(LocalDate(2026, 9, 6)),
            CadencePlanner.nextOccurrence(
                ReminderCadence.Monthly,
                start,
                from = LocalDate(2026, 8, 7),
            ),
        )
    }

    @Test
    fun monthlyClampsToShorterMonths() {
        val endOfMonth = LocalDate(2026, 1, 31)
        assertEquals(
            onDate(LocalDate(2026, 2, 28)),
            CadencePlanner.nextOccurrence(
                ReminderCadence.Monthly,
                endOfMonth,
                from = LocalDate(2026, 2, 1),
            ),
        )
    }

    @Test
    fun monthlyRecoversFromTheClampInLongerMonths() {
        // Anchored on the 31st: February clamps to the 28th, but March is the 31st
        // again because every candidate is computed from the anchor.
        val endOfMonth = LocalDate(2026, 1, 31)
        assertEquals(
            onDate(LocalDate(2026, 3, 31)),
            CadencePlanner.nextOccurrence(
                ReminderCadence.Monthly,
                endOfMonth,
                from = LocalDate(2026, 3, 1),
            ),
        )
    }

    @Test
    fun monthlyOnAnOccurrenceDayFiresThatDay() {
        assertEquals(
            onDate(LocalDate(2026, 10, 6)),
            CadencePlanner.nextOccurrence(
                ReminderCadence.Monthly,
                start,
                from = LocalDate(2026, 10, 6),
            ),
        )
    }

    /* ---- EveryDistance ---- */

    @Test
    fun distanceTargetIsOneStepPastTheAnchor() {
        assertEquals(
            ReminderOccurrence.AtOdometer(52_000),
            CadencePlanner.nextOccurrence(
                ReminderCadence.EveryDistance(10_000),
                start,
                from = start,
                anchorKm = 42_000,
            ),
        )
    }

    @Test
    fun distanceTargetHoldsStillOnceTheCarPassesIt() {
        // A reached target stays due until the owner acts; it must not slide ahead
        // of the odometer, or the nudge could never fire.
        assertEquals(
            ReminderOccurrence.AtOdometer(52_000),
            CadencePlanner.nextOccurrence(
                ReminderCadence.EveryDistance(10_000),
                start,
                from = start.plus(30, DateTimeUnit.DAY),
                anchorKm = 42_000,
            ),
        )
    }

    @Test
    fun distanceWithoutAnAnchorHasNoAnswer() {
        assertNull(
            CadencePlanner.nextOccurrence(
                ReminderCadence.EveryDistance(10_000),
                start,
                from = start,
                anchorKm = null,
            ),
        )
    }

    /* ---- Aggregate overload ---- */

    @Test
    fun pausedReminderNeverFires() {
        val reminder = reminder().withPaused(true)
        assertNull(CadencePlanner.nextOccurrence(reminder, from = start))
    }

    @Test
    fun liveReminderDelegatesToItsCadence() {
        assertEquals(
            onDate(LocalDate(2026, 8, 21)),
            CadencePlanner.nextOccurrence(reminder(), from = LocalDate(2026, 8, 10)),
        )
    }

    @Test
    fun dayAfterADismissedOccurrenceFindsTheOneBehindIt() {
        val dismissed = LocalDate(2026, 8, 21)
        assertEquals(
            onDate(LocalDate(2026, 9, 5)),
            CadencePlanner.nextOccurrence(
                reminder(),
                from = dismissed.plus(1, DateTimeUnit.DAY),
            ),
        )
    }

    private fun reminder(): CustomReminder = CustomReminder.create(
        id = ReminderId("rem-1"),
        ownerId = OwnerId("owner-1"),
        carId = CarId("car-1"),
        title = "Air pressure check",
        cadence = ReminderCadence.EveryDays(15),
        startsOn = start,
        at = LocalTime(9, 0),
        today = start,
    ).getOrNull()!!
}
