package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.reminder.model.ReminderUrgency
import com.hopcape.odo.core.domain.reminder.model.UpcomingReminder
import com.hopcape.odo.core.domain.reminder.policy.ReminderOccurrence
import com.hopcape.odo.feature.reminders.FakeDocumentRepository
import com.hopcape.odo.feature.reminders.FakeReminderRepository
import com.hopcape.odo.feature.reminders.FakeServiceLogRepository
import com.hopcape.odo.feature.reminders.TEST_CAR
import com.hopcape.odo.feature.reminders.TEST_CLOCK
import com.hopcape.odo.feature.reminders.TEST_TODAY
import com.hopcape.odo.feature.reminders.customReminder
import com.hopcape.odo.feature.reminders.document
import com.hopcape.odo.feature.reminders.reading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ObserveRemindersUseCaseTest {

    private fun useCase(
        documents: FakeDocumentRepository = FakeDocumentRepository(),
        serviceLogs: FakeServiceLogRepository = FakeServiceLogRepository(),
        reminders: FakeReminderRepository = FakeReminderRepository(),
    ) = ObserveRemindersUseCase(
        documents = documents,
        serviceLogs = serviceLogs,
        reminders = reminders,
        clock = TEST_CLOCK,
        timeZone = TimeZone.UTC,
    )

    @Test
    fun buildsTheFeedFromEverySource() = runTest {
        val feed = useCase(
            documents = FakeDocumentRepository(
                listOf(document(expiresOn = TEST_TODAY.plus(5, DateTimeUnit.DAY))),
            ),
            reminders = FakeReminderRepository(
                listOf(customReminder(startsOn = TEST_TODAY.plus(10, DateTimeUnit.DAY))),
            ),
        ).invoke(TEST_CAR).first()

        assertEquals(1, feed.thisWeek.size)
        assertEquals(ReminderKind.INSURANCE_EXPIRY, feed.thisWeek.single().kind)
        assertEquals(1, feed.upcoming.size)
        assertIs<UpcomingReminder.Custom>(feed.upcoming.single())
    }

    @Test
    fun anEmptyGarageIsAnEmptyFeedWithEverySuggestion() = runTest {
        val feed = useCase().invoke(TEST_CAR).first()

        assertTrue(feed.thisWeek.isEmpty() && feed.upcoming.isEmpty())
        assertEquals(ReminderPreset.entries.toList(), feed.suggestions)
    }

    @Test
    fun theOdometerReadingDrivesDistanceUrgency() = runTest {
        val feed = useCase(
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 52_500))),
            reminders = FakeReminderRepository(
                listOf(
                    customReminder(
                        cadence = ReminderCadence.EveryDistance(10_000),
                        anchorKm = 42_000,
                    ),
                ),
            ),
        ).invoke(TEST_CAR).first()

        // 52,500 km driven past a 52,000 km target: due now.
        val row = feed.thisWeek.single()
        assertIs<UpcomingReminder.Custom>(row)
        assertEquals(ReminderOccurrence.AtOdometer(52_000), row.occurrence)
    }

    @Test
    fun documentsOfAnotherCarStayOut() = runTest {
        val feed = useCase(
            documents = FakeDocumentRepository(
                listOf(document(expiresOn = TEST_TODAY.plus(5, DateTimeUnit.DAY))),
            ),
        ).invoke(com.hopcape.odo.core.domain.car.model.CarId("car-2")).first()

        assertTrue(feed.thisWeek.isEmpty() && feed.upcoming.isEmpty())
    }

    @Test
    fun untakenPresetsAreSuggested() = runTest {
        val feed = useCase(
            reminders = FakeReminderRepository(
                listOf(customReminder(preset = ReminderPreset.BATTERY)),
            ),
        ).invoke(TEST_CAR).first()

        assertTrue(ReminderPreset.BATTERY !in feed.suggestions)
        assertContains(feed.suggestions, ReminderPreset.AIR_PRESSURE)
    }

    @Test
    fun aLapsedDocumentYieldsNoRow() = runTest {
        val feed = useCase(
            documents = FakeDocumentRepository(
                listOf(document(type = DocumentType.PUC, expiresOn = TEST_TODAY.plus(-2, DateTimeUnit.DAY))),
            ),
        ).invoke(TEST_CAR).first()

        assertTrue(feed.thisWeek.isEmpty() && feed.upcoming.isEmpty())
    }

    @Test
    fun urgencyBucketsAgreeWithTheHeaderTheScreenWillDraw() = runTest {
        val feed = useCase(
            documents = FakeDocumentRepository(
                listOf(document(expiresOn = TEST_TODAY.plus(200, DateTimeUnit.DAY))),
            ),
        ).invoke(TEST_CAR).first()

        assertEquals(ReminderUrgency.ON_TRACK, feed.upcoming.single().urgency)
    }
}
