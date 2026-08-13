package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.reminders.FakeReminderRepository
import com.hopcape.odo.feature.reminders.FixedIdGenerator
import com.hopcape.odo.feature.reminders.RecordingScheduler
import com.hopcape.odo.feature.reminders.TEST_CAR
import com.hopcape.odo.feature.reminders.TEST_CLOCK
import com.hopcape.odo.feature.reminders.TEST_OWNER
import com.hopcape.odo.feature.reminders.TEST_TODAY
import com.hopcape.odo.feature.reminders.customReminder
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomReminderWriteUseCasesTest {

    private val command = CustomReminderCommand(
        title = "Coolant top-up",
        cadence = ReminderCadence.Monthly,
        startsOn = TEST_TODAY.plus(1, DateTimeUnit.DAY),
        at = LocalTime(9, 0),
    )

    private class Fixture(
        initial: List<com.hopcape.odo.core.domain.reminder.model.CustomReminder> = emptyList(),
        failWith: DomainError? = null,
    ) {
        val reminders = FakeReminderRepository(initial, failWith = failWith)
        val scheduler = RecordingScheduler()
        val create = CreateCustomReminderUseCase(
            reminders = reminders,
            scheduler = scheduler,
            idGenerator = FixedIdGenerator(),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        )
        val update = UpdateCustomReminderUseCase(
            reminders = reminders,
            scheduler = scheduler,
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        )
        val setPaused = SetReminderPausedUseCase(reminders = reminders, scheduler = scheduler)
        val delete = DeleteCustomReminderUseCase(reminders = reminders, scheduler = scheduler)
        val dismiss = DismissReminderUseCase(reminders = reminders)
    }

    /* ---- Create ---- */

    @Test
    fun createStoresAndSchedules() = runTest {
        val fixture = Fixture()

        val result = fixture.create(command, TEST_CAR, TEST_OWNER)

        val stored = result.getOrNull()!!
        assertEquals("rem-new", stored.id.value)
        assertEquals(listOf(stored), fixture.reminders.customs)
        assertEquals(1, fixture.scheduler.refreshes)
    }

    @Test
    fun createRejectsBadInputWithoutTouchingAnything() = runTest {
        val fixture = Fixture()

        val result = fixture.create(command.copy(title = " "), TEST_CAR, TEST_OWNER)

        assertContains(result.leftOrNull()!!, DomainError.BlankReminderTitle)
        assertTrue(fixture.reminders.customs.isEmpty())
        assertEquals(0, fixture.scheduler.refreshes)
    }

    @Test
    fun createDoesNotScheduleWhenTheWriteFails() = runTest {
        val fixture = Fixture(failWith = DomainError.PersistenceFailure("disk full"))

        val result = fixture.create(command, TEST_CAR, TEST_OWNER)

        assertTrue(result.isLeft())
        assertEquals(0, fixture.scheduler.refreshes)
    }

    /* ---- Update ---- */

    @Test
    fun updateRevalidatesAndReschedules() = runTest {
        val existing = customReminder()
        val fixture = Fixture(initial = listOf(existing))

        val result = fixture.update(existing.id, command)

        val stored = result.getOrNull()!!
        assertEquals("Coolant top-up", stored.title.value)
        assertEquals(existing.id, stored.id)
        assertEquals(1, fixture.scheduler.refreshes)
    }

    @Test
    fun updateOfAMissingReminderSaysSo() = runTest {
        val fixture = Fixture()

        val result = fixture.update(ReminderId("rem-gone"), command)

        assertContains(result.leftOrNull()!!, DomainError.ReminderNotFound)
    }

    @Test
    fun updateKeepsAPausedReminderPausedAndCancelsInstead() = runTest {
        val existing = customReminder(paused = true)
        val fixture = Fixture(initial = listOf(existing))

        val stored = fixture.update(existing.id, command).getOrNull()!!

        assertTrue(stored.paused)
        // A paused reminder is dropped by the rebuild, so the write still asks for one.
        assertEquals(1, fixture.scheduler.refreshes)
    }

    /* ---- Pause / resume ---- */

    @Test
    fun pausingCancelsTheSchedule() = runTest {
        val existing = customReminder()
        val fixture = Fixture(initial = listOf(existing))

        val stored = fixture.setPaused(existing.id, paused = true).getOrNull()!!

        assertTrue(stored.paused)
        assertEquals(1, fixture.scheduler.refreshes)
    }

    @Test
    fun resumingReschedules() = runTest {
        val existing = customReminder(paused = true)
        val fixture = Fixture(initial = listOf(existing))

        val stored = fixture.setPaused(existing.id, paused = false).getOrNull()!!

        assertTrue(!stored.paused)
        assertEquals(1, fixture.scheduler.refreshes)
    }

    /* ---- Delete ---- */

    @Test
    fun deleteRemovesTheRowAndTheSchedule() = runTest {
        val existing = customReminder()
        val fixture = Fixture(initial = listOf(existing))

        assertTrue(fixture.delete(existing.id).isRight())

        assertTrue(fixture.reminders.customs.isEmpty())
        assertEquals(1, fixture.scheduler.refreshes)
    }

    @Test
    fun aFailedDeleteLeavesTheScheduleAlone() = runTest {
        val existing = customReminder()
        val fixture = Fixture(
            initial = listOf(existing),
            failWith = DomainError.PersistenceFailure("disk full"),
        )

        assertTrue(fixture.delete(existing.id).isLeft())
        assertEquals(0, fixture.scheduler.refreshes)
    }

    /* ---- Dismiss ---- */

    @Test
    fun dismissRecordsTheOccurrence() = runTest {
        val fixture = Fixture()
        val dismissal = ReminderDismissal(ReminderKind.INSURANCE_EXPIRY, TEST_TODAY)

        assertTrue(fixture.dismiss(TEST_CAR, dismissal).isRight())

        assertEquals(listOf(dismissal), fixture.reminders.recordedDismissals)
    }
}
