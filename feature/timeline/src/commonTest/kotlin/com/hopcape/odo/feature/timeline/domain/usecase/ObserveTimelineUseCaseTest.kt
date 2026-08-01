package com.hopcape.odo.feature.timeline.domain.usecase

import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.timeline.FakeCarRepository
import com.hopcape.odo.feature.timeline.FakeDocumentRepository
import com.hopcape.odo.feature.timeline.FakeHealthScoreRepository
import com.hopcape.odo.feature.timeline.FakeServiceLogRepository
import com.hopcape.odo.feature.timeline.TEST_CAR
import com.hopcape.odo.feature.timeline.domain.model.ActivityCategory
import com.hopcape.odo.feature.timeline.testCar
import com.hopcape.odo.feature.timeline.testDocument
import com.hopcape.odo.feature.timeline.testEntry
import com.hopcape.odo.feature.timeline.testSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveTimelineUseCaseTest {

    private val delhi = TimeZone.of("Asia/Kolkata")

    private fun useCase(
        cars: FakeCarRepository = FakeCarRepository(),
        logs: FakeServiceLogRepository = FakeServiceLogRepository(),
        documents: FakeDocumentRepository = FakeDocumentRepository(),
        scores: FakeHealthScoreRepository = FakeHealthScoreRepository(),
    ) = ObserveTimelineUseCase(cars, logs, documents, scores, delhi)

    @Test
    fun feed_mergesEverySource() = runTest {
        val snapshot = useCase(
            logs = FakeServiceLogRepository(listOf(testEntry("l1", LocalDate(2026, 7, 12)))),
            documents = FakeDocumentRepository(listOf(testDocument(addedOn = LocalDate(2026, 6, 1)))),
            scores = FakeHealthScoreRepository(
                listOf(
                    testSnapshot("before", "2026-07-06T10:00:00Z", total = 70),
                    testSnapshot("after", "2026-07-07T10:00:00Z", total = 74),
                ),
            ),
        ).invoke(TEST_CAR).first()

        assertEquals("Swift VXI", snapshot.carName)
        assertEquals(
            listOf(
                LocalDate(2026, 7, 12),
                LocalDate(2026, 7, 7),
                LocalDate(2026, 6, 1),
                LocalDate(2026, 1, 5),
            ),
            snapshot.events.map { it.date },
        )
    }

    @Test
    fun counts_areOfWhatExistsNotOfWhatIsShown() = runTest {
        val snapshot = useCase(
            logs = FakeServiceLogRepository(
                listOf(
                    testEntry("l1", LocalDate(2026, 7, 12)),
                    testEntry("l2", LocalDate(2026, 7, 8)),
                ),
            ),
            documents = FakeDocumentRepository(
                listOf(
                    testDocument(DocumentType.PUC, addedOn = LocalDate(2026, 6, 2)),
                    testDocument(DocumentType.INSURANCE, addedOn = LocalDate(2026, 6, 1)),
                ),
            ),
        ).invoke(TEST_CAR).first()

        assertEquals(2, snapshot.countOf(ActivityCategory.SERVICES))
        assertEquals(2, snapshot.countOf(ActivityCategory.DOCUMENTS))
        assertEquals(0, snapshot.countOf(ActivityCategory.SCORE))
        assertEquals(1, snapshot.countOf(ActivityCategory.MILESTONES))
    }

    @Test
    fun feed_reEmitsWhenAnySourceChanges() = runTest {
        val logs = FakeServiceLogRepository()
        val documents = FakeDocumentRepository()
        val feed = useCase(logs = logs, documents = documents).invoke(TEST_CAR)

        // Only the milestone to begin with.
        assertEquals(1, feed.first().events.size)

        logs.emit(listOf(testEntry("l1", LocalDate(2026, 7, 12))))
        assertEquals(2, feed.first().events.size)

        // A policy uploaded from the vault while the tab is open.
        documents.emit(listOf(testDocument(addedOn = LocalDate(2026, 7, 30))))
        assertEquals(3, feed.first().events.size)
    }

    @Test
    fun feed_isJustTheMilestoneForAFreshCar() = runTest {
        val snapshot = useCase().invoke(TEST_CAR).first()

        assertEquals(1, snapshot.events.size)
        assertTrue(snapshot.events.single() is ActivityEvent.CarAdded)
    }

    @Test
    fun feed_hasNothingAtAllForACarThatIsNotThere() = runTest {
        val snapshot = useCase(cars = FakeCarRepository(car = null)).invoke(TEST_CAR).first()

        assertEquals("", snapshot.carName)
        assertEquals(emptyList(), snapshot.events)
    }

    @Test
    fun car_isNamedTheWayEverySurfaceNamesIt() = runTest {
        val snapshot = useCase(cars = FakeCarRepository(testCar(nickname = null)))
            .invoke(TEST_CAR)
            .first()

        assertEquals("Maruti Suzuki Swift VXI", snapshot.carName)
    }
}
