package com.hopcape.odo.feature.documentvault.presentation

import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.entitlement.DocumentLimit
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.documentvault.FakeActiveCarProvider
import com.hopcape.odo.feature.documentvault.FakeDocumentFileStore
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.FixedIdGenerator
import com.hopcape.odo.feature.documentvault.RecordingAnalytics
import com.hopcape.odo.feature.documentvault.TEST_CAR
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.TEST_OWNER_PROVIDER
import com.hopcape.odo.feature.documentvault.document
import com.hopcape.odo.feature.documentvault.domain.usecase.AddDocumentUseCase
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentEffect
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentEvent
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentViewModel
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.feature.documentvault.presentation.state.Submission
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_error_limit_reached
import com.hopcape.odo.feature.documentvault.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.hopcape.odo.core.domain.document.model.Document as DomainDocument

class AddDocumentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        prefillType: DocumentType? = null,
        held: List<DomainDocument> = emptyList(),
        limit: DocumentLimit = DocumentLimit.UpTo(3),
        hasCar: Boolean = true,
        analytics: RecordingAnalytics = RecordingAnalytics(),
        repository: FakeDocumentRepository = FakeDocumentRepository(held),
        files: FakeDocumentFileStore = FakeDocumentFileStore(),
    ) = AddDocumentViewModel(
        prefillType = prefillType,
        addDocument = AddDocumentUseCase(
            documents = repository,
            files = files,
            allowance = DocumentAllowance { limit },
            idGenerator = FixedIdGenerator("doc-new"),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        ),
        activeCar = FakeActiveCarProvider(if (hasCar) TEST_CAR else null),
        currentOwner = TEST_OWNER_PROVIDER,
        telemetry = testTelemetry(analytics),
    )

    @Test
    fun opensOnTheTypeTheRouteAskedFor() = runTest(dispatcher) {
        assertEquals(DocumentType.PUC, viewModel(prefillType = DocumentType.PUC).state.value.selectedType)
    }

    @Test
    fun withoutAPrefill_opensOnInsurance() = runTest(dispatcher) {
        assertEquals(DocumentType.INSURANCE, viewModel().state.value.selectedType)
    }

    @Test
    fun pickingAFile_savesTheDocumentAndMovesOn() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val files = FakeDocumentFileStore()
        val viewModel = viewModel(prefillType = DocumentType.RC, repository = repository, files = files)

        viewModel.onEvent(AddDocumentEvent.Capture.FilePicked("content://downloads/rc.pdf"))
        advanceUntilIdle()

        val stored = repository.observe(TEST_CAR).first().single()
        assertEquals(DocumentType.RC, stored.type)
        assertEquals("documents/car-1/doc-new.pdf", stored.storagePath)
        assertTrue(files.saved.contains(stored.storagePath))
        assertEquals(Submission.Succeeded, viewModel.state.value.submission)
    }

    @Test
    fun aSavedDocument_opensItsConfirmation() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(AddDocumentEvent.Capture.FilePicked("content://downloads/policy.pdf"))
        advanceUntilIdle()

        val effect = assertIs<AddDocumentEffect.OpenSuccess>(viewModel.effects.first())
        assertEquals("doc-new", effect.id.value)
    }

    @Test
    fun aCancelledPicker_changesNothing() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(AddDocumentEvent.Capture.FilePicked(null))
        advanceUntilIdle()

        assertEquals(Submission.Idle, viewModel.state.value.submission)
    }

    @Test
    fun aFullPlan_failsNamingTheLimitInsteadOfSaving() = runTest(dispatcher) {
        val repository = FakeDocumentRepository(
            List(3) { document("held-$it", DocumentType.OTHER, expiresOn = null) },
        )
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(repository = repository, limit = DocumentLimit.UpTo(3), analytics = analytics)

        viewModel.onEvent(AddDocumentEvent.Capture.FilePicked("content://downloads/policy.pdf"))
        advanceUntilIdle()

        // The refusal names its reason. "Something went wrong" here read as a broken app,
        // and the owner's next step (delete one, or upgrade) was invisible.
        val failure = assertIs<Submission.Failed>(viewModel.state.value.submission)
        assertEquals(UiText(Res.string.dv_error_limit_reached, listOf(3)), failure.message)
        assertEquals(3, repository.observe(TEST_CAR).first().size, "nothing was added")
        assertTrue(
            analytics.names().contains(DocumentVaultTelemetry.Event.LIMIT_REACHED),
            "a full plan is a pricing signal, not a bug: ${analytics.names()}",
        )
    }

    @Test
    fun withoutACar_theOwnerIsToldRatherThanFailedSilently() = runTest(dispatcher) {
        val viewModel = viewModel(hasCar = false)

        viewModel.onEvent(AddDocumentEvent.Capture.FilePicked("content://downloads/policy.pdf"))
        advanceUntilIdle()

        assertNotNull(assertIs<Submission.Failed>(viewModel.state.value.submission).message)
    }

    @Test
    fun anUnbuiltCaptureMethod_saysSoAndIsCounted() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)

        viewModel.onEvent(AddDocumentEvent.Capture.Scan)
        advanceUntilIdle()

        assertIs<Submission.Failed>(viewModel.state.value.submission)
        val (_, properties) = analytics.events.first { it.first == DocumentVaultTelemetry.Event.CAPTURE_UNAVAILABLE }
        assertEquals(DocumentVaultTelemetry.CaptureMethod.SCAN, properties[DocumentVaultTelemetry.Key.METHOD])
    }

    @Test
    fun changingTypeClearsAStaleFailure() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEvent(AddDocumentEvent.Capture.DigiLocker)
        advanceUntilIdle()

        viewModel.onEvent(AddDocumentEvent.TypeSelected(DocumentType.LOAN))

        assertEquals(Submission.Idle, viewModel.state.value.submission)
        assertEquals(DocumentType.LOAN, viewModel.state.value.selectedType)
    }

    @Test
    fun documentsAddedWithoutAnExpiry_areStoredAsSuch() = runTest(dispatcher) {
        // The screen has no date fields yet, so nothing may invent one.
        val repository = FakeDocumentRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.onEvent(AddDocumentEvent.Capture.FilePicked("content://downloads/policy.pdf"))
        advanceUntilIdle()

        val stored = repository.observe(TEST_CAR).first().single()
        assertEquals(null, stored.expiresOn)
        assertEquals(null, stored.issuedOn)
    }
}
