package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.record.model.RecordStatus
import com.hopcape.odo.feature.servicelog.presentation.FakeCarRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeDocumentRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeHealthScoreRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeOwnerProfileRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import com.hopcape.odo.feature.servicelog.presentation.TEST_CAR
import com.hopcape.odo.feature.servicelog.presentation.TEST_CLOCK
import com.hopcape.odo.feature.servicelog.presentation.testCar
import com.hopcape.odo.feature.servicelog.presentation.testDocument
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.testOwner
import com.hopcape.odo.feature.servicelog.presentation.testSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The record read is a combine over five ports. What is worth testing is that every one of
 * them reaches the emitted document, and that a change in any one re-emits — the sheet stays
 * open while the owner picks a target, so a service logged in that window belongs in the
 * file they are about to send.
 */
class ObserveServiceRecordUseCaseTest {

    private fun useCase(
        cars: FakeCarRepository = FakeCarRepository(testCar()),
        logs: FakeServiceLogRepository = FakeServiceLogRepository(),
        documents: FakeDocumentRepository = FakeDocumentRepository(),
        scores: FakeHealthScoreRepository = FakeHealthScoreRepository(),
        owners: FakeOwnerProfileRepository = FakeOwnerProfileRepository(testOwner()),
    ) = ObserveServiceRecordUseCase(
        cars = cars,
        logs = logs,
        documents = documents,
        scores = scores,
        owners = owners,
        clock = TEST_CLOCK,
        timeZone = TimeZone.UTC,
    )

    @Test
    fun `every port reaches the emitted record`() = runTest {
        val record = useCase(
            logs = FakeServiceLogRepository(
                listOf(testEntry("a", km = 54_000, paise = 320_000, verified = true, date = LocalDate(2026, 7, 12))),
            ),
            documents = FakeDocumentRepository(listOf(testDocument(DocumentType.INSURANCE))),
            scores = FakeHealthScoreRepository(listOf(testSnapshot("2026-07-02T10:00:00Z", total = 74))),
        ).invoke(TEST_CAR).first()

        assertEquals("Maruti Swift VXI", record.carName, "the car port")
        assertEquals("Rahul Deshmukh", record.ownership.ownerName, "the owner port")
        assertEquals(74, record.healthScore, "the score port")
        assertEquals(listOf(DocumentType.INSURANCE), record.documents.map { it.type }, "the document port")
        assertTrue(record.rows.any { it.status == RecordStatus.VERIFIED }, "the service-log port")
    }

    @Test
    fun `a service logged while the sheet is open lands in the record`() = runTest {
        val logs = FakeServiceLogRepository()
        val counts = mutableListOf<Int>()

        // Collected on the background scope: the flow never completes, and a plain launch
        // would leave runTest waiting on it forever. Unconfined so the collector is running
        // before the update below, rather than only once the scheduler is advanced.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(logs = logs).invoke(TEST_CAR).collect { counts += it.entryCount }
        }
        advanceUntilIdle()

        assertEquals(listOf(1), counts, "the opening milestone alone")

        logs.entries.update { it + testEntry("later", km = 54_000, date = LocalDate(2026, 7, 12)) }
        advanceUntilIdle()

        assertEquals(listOf(1, 2), counts, "the new service re-emits the whole record")
    }

    @Test
    fun `the document is dated by the clock, not by its contents`() = runTest {
        // TEST_CLOCK is fixed at 2026-07-03T10:00:00Z.
        assertEquals(LocalDate(2026, 7, 3), useCase().invoke(TEST_CAR).first().issuedOn)
    }

    @Test
    fun `a car that is still loading yields an empty record rather than nothing`() = runTest {
        val record = useCase(cars = FakeCarRepository(initial = null)).invoke(TEST_CAR).first()

        assertTrue(record.isEmpty)
        assertEquals("", record.carName)
    }
}
