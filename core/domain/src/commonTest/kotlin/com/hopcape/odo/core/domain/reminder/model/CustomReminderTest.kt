package com.hopcape.odo.core.domain.reminder.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CustomReminderTest {

    private val today = LocalDate(2026, 8, 6)

    private fun create(
        title: String? = "Air pressure check",
        cadence: ReminderCadence = ReminderCadence.EveryDays(15),
        startsOn: LocalDate = today,
        anchorKm: Int? = null,
        preset: ReminderPreset? = ReminderPreset.AIR_PRESSURE,
    ) = CustomReminder.create(
        id = ReminderId("rem-1"),
        ownerId = OwnerId("owner-1"),
        carId = CarId("car-1"),
        title = title,
        cadence = cadence,
        startsOn = startsOn,
        at = LocalTime(9, 0),
        today = today,
        preset = preset,
        anchorKm = anchorKm,
    )

    @Test
    fun buildsAValidReminder() {
        val reminder = create().getOrNull()!!

        assertEquals("Air pressure check", reminder.title.value)
        assertEquals(ReminderCadence.EveryDays(15), reminder.cadence)
        assertEquals(false, reminder.paused)
        assertNull(reminder.addedOn)
    }

    @Test
    fun trimsTheTitle() {
        val reminder = create(title = "  Coolant top-up  ").getOrNull()!!
        assertEquals("Coolant top-up", reminder.title.value)
    }

    @Test
    fun rejectsABlankTitle() {
        val errors = create(title = "   ").leftOrNull()!!
        assertContains(errors, DomainError.BlankReminderTitle)
    }

    @Test
    fun rejectsAnOverlongTitle() {
        val errors = create(title = "x".repeat(ReminderTitle.MAX_LENGTH + 1)).leftOrNull()!!
        assertContains(errors, DomainError.ReminderTitleTooLong(ReminderTitle.MAX_LENGTH))
    }

    @Test
    fun rejectsANonPositiveDayStep() {
        val errors = create(cadence = ReminderCadence.EveryDays(0)).leftOrNull()!!
        assertContains(errors, DomainError.ReminderIntervalNotPositive)
    }

    @Test
    fun rejectsANonPositiveDistanceStep() {
        val errors = create(
            cadence = ReminderCadence.EveryDistance(0),
            anchorKm = 42_000,
        ).leftOrNull()!!
        assertContains(errors, DomainError.ReminderIntervalNotPositive)
    }

    @Test
    fun rejectsAStartInThePast() {
        val errors = create(startsOn = LocalDate(2026, 8, 5)).leftOrNull()!!
        assertContains(errors, DomainError.ReminderStartInPast)
    }

    @Test
    fun allowsAStartToday() {
        assertTrue(create(startsOn = today).isRight())
    }

    @Test
    fun distanceCadenceNeedsAnAnchor() {
        val errors = create(
            cadence = ReminderCadence.EveryDistance(10_000),
            anchorKm = null,
        ).leftOrNull()!!
        assertContains(errors, DomainError.MissingReminderAnchorOdometer)
    }

    @Test
    fun rejectsANegativeAnchor() {
        val errors = create(
            cadence = ReminderCadence.EveryDistance(10_000),
            anchorKm = -1,
        ).leftOrNull()!!
        assertContains(errors, DomainError.NegativeOdometer)
    }

    @Test
    fun dropsTheAnchorForACalendarCadence() {
        val reminder = create(cadence = ReminderCadence.Monthly, anchorKm = 42_000).getOrNull()!!
        assertNull(reminder.anchorKm)
    }

    @Test
    fun keepsTheAnchorForADistanceCadence() {
        val reminder = create(
            cadence = ReminderCadence.EveryDistance(10_000),
            anchorKm = 42_000,
        ).getOrNull()!!
        assertEquals(42_000, reminder.anchorKm)
    }

    @Test
    fun accumulatesEveryFailureAtOnce() {
        val errors = create(
            title = " ",
            cadence = ReminderCadence.EveryDistance(-5),
            startsOn = LocalDate(2026, 1, 1),
            anchorKm = null,
        ).leftOrNull()!!

        assertContains(errors, DomainError.BlankReminderTitle)
        assertContains(errors, DomainError.ReminderIntervalNotPositive)
        assertContains(errors, DomainError.ReminderStartInPast)
        assertContains(errors, DomainError.MissingReminderAnchorOdometer)
    }

    @Test
    fun pausesAndResumes() {
        val reminder = create().getOrNull()!!

        val paused = reminder.withPaused(true)
        assertTrue(paused.paused)
        assertEquals(reminder.id, paused.id)
        assertEquals(reminder.title, paused.title)

        assertSame(reminder, reminder.withPaused(false))
    }

    @Test
    fun reconstituteAcceptsAStartInThePast() {
        val reminder = CustomReminder.reconstitute(
            id = ReminderId("rem-1"),
            ownerId = OwnerId("owner-1"),
            carId = CarId("car-1"),
            title = "Battery check",
            cadence = ReminderCadence.Monthly,
            startsOn = LocalDate(2025, 1, 1),
            at = LocalTime(9, 0),
            paused = true,
            addedOn = LocalDate(2025, 1, 1),
        )

        assertEquals(LocalDate(2025, 1, 1), reminder.startsOn)
        assertTrue(reminder.paused)
    }

    @Test
    fun reconstituteFailsFastOnACorruptTitle() {
        assertFailsWith<IllegalStateException> {
            CustomReminder.reconstitute(
                id = ReminderId("rem-1"),
                ownerId = OwnerId("owner-1"),
                carId = CarId("car-1"),
                title = "",
                cadence = ReminderCadence.Once,
                startsOn = today,
                at = LocalTime(9, 0),
                paused = false,
                addedOn = null,
            )
        }
    }

    @Test
    fun dismissalRequiresTheCustomIdExactlyForCustomKind() {
        assertFailsWith<IllegalArgumentException> {
            ReminderDismissal(kind = ReminderKind.CUSTOM, dueOn = today)
        }
        assertFailsWith<IllegalArgumentException> {
            ReminderDismissal(
                kind = ReminderKind.INSURANCE_EXPIRY,
                dueOn = today,
                customId = ReminderId("rem-1"),
            )
        }
    }
}
