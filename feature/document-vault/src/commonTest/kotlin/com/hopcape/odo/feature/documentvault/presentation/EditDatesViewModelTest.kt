package com.hopcape.odo.feature.documentvault.presentation

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.documentvault.FakeDocumentFileStore
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.RecordingReminderScheduler
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.document
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.UpdateDocumentUseCase
import com.hopcape.odo.feature.documentvault.presentation.dates.EditDatesEffect
import com.hopcape.odo.feature.documentvault.presentation.dates.EditDatesEvent
import com.hopcape.odo.feature.documentvault.presentation.dates.EditDatesViewModel
import com.hopcape.odo.feature.documentvault.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The sheet that gives a document its missing expiry — the only way a paper filed before the
 * app read dates can start producing reminders.
 */
class EditDatesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val undated = document(
        id = "d1",
        type = DocumentType.INSURANCE,
        expiresOn = null,
        title = "SafeDrive",
    )

    private fun viewModel(
        repository: FakeDocumentRepository,
        reminders: RecordingReminderScheduler = RecordingReminderScheduler(),
    ) = EditDatesViewModel(
        documentId = DocumentId("d1"),
        observeDetail = ObserveDocumentDetailUseCase(
            documents = repository,
            files = FakeDocumentFileStore(stored = setOf(undated.storagePath)),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        ),
        updateDocument = UpdateDocumentUseCase(
            documents = repository,
            reminders = reminders,
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        ),
        telemetry = testTelemetry(),
    )

    @Test
    fun opensOnTheDatesTheDocumentAlreadyHas() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDocumentRepository(listOf(undated)))
        advanceUntilIdle()

        assertEquals(DocumentType.INSURANCE, viewModel.state.value.type)
        assertEquals(null, viewModel.state.value.expiresOn)
        assertFalse(viewModel.state.value.canSave, "a paper that renews needs a date first")
    }

    @Test
    fun savingAnExpiry_storesItAndSchedulesTheReminder() = runTest(dispatcher) {
        val repository = FakeDocumentRepository(listOf(undated))
        val reminders = RecordingReminderScheduler()
        val viewModel = viewModel(repository, reminders)
        advanceUntilIdle()

        viewModel.onEvent(EditDatesEvent.ExpiresOnChanged(LocalDate(2027, 3, 14)))
        assertTrue(viewModel.state.value.canSave)
        viewModel.onEvent(EditDatesEvent.SaveTapped)
        advanceUntilIdle()

        val stored = repository.observe(DocumentId("d1")).first()!!
        assertEquals(LocalDate(2027, 3, 14), stored.expiresOn)
        assertEquals(1, reminders.refreshes)
        assertIs<EditDatesEffect.Dismiss>(viewModel.effects.first())
    }

    /** The sheet edits dates and nothing else — the owner's own label survives a save. */
    @Test
    fun savingKeepsTheTitleAndTheType() = runTest(dispatcher) {
        val repository = FakeDocumentRepository(listOf(undated))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(EditDatesEvent.ExpiresOnChanged(LocalDate(2027, 3, 14)))
        viewModel.onEvent(EditDatesEvent.SaveTapped)
        advanceUntilIdle()

        val stored = repository.observe(DocumentId("d1")).first()!!
        assertEquals("SafeDrive", stored.title?.value)
        assertEquals(DocumentType.INSURANCE, stored.type)
    }

    /** An RC never renews, so the sheet does not hold its save hostage to a date. */
    @Test
    fun aPaperThatNeverExpires_canBeSavedWithNoDate() = runTest(dispatcher) {
        val rc = document(id = "d1", type = DocumentType.RC, expiresOn = null)
        val viewModel = viewModel(FakeDocumentRepository(listOf(rc)))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canSave)
    }
}
