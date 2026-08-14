package com.hopcape.odo.feature.documentvault.presentation

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.FakeDocumentFileStore
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.RecordingDownloads
import com.hopcape.odo.feature.documentvault.RecordingReminderScheduler
import com.hopcape.odo.feature.documentvault.RecordingAnalytics
import com.hopcape.odo.feature.documentvault.TEST_CAR
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.document
import com.hopcape.odo.feature.documentvault.domain.usecase.DeleteDocumentUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ReplaceDocumentFileUseCase
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailContent
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailEffect
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailEvent
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailViewModel
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import com.hopcape.odo.feature.documentvault.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val insurance = document(
        id = "d1",
        type = DocumentType.INSURANCE,
        issuedOn = LocalDate(2025, 8, 4),
        expiresOn = LocalDate(2026, 8, 4),
        source = DocumentSource.DIGILOCKER,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        documentId: String = "d1",
        repository: FakeDocumentRepository = FakeDocumentRepository(listOf(insurance)),
        files: FakeDocumentFileStore = FakeDocumentFileStore(stored = setOf(insurance.storagePath)),
        downloads: RecordingDownloads = RecordingDownloads(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ): DocumentDetailViewModel {
        val observeDetail = ObserveDocumentDetailUseCase(
            documents = repository,
            files = files,
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        )
        return DocumentDetailViewModel(
            documentId = DocumentId(documentId),
            observeDetail = observeDetail,
            deleteDocument = DeleteDocumentUseCase(repository, files, RecordingReminderScheduler()),
            replaceFile = ReplaceDocumentFileUseCase(repository, files),
            downloads = downloads,
            telemetry = testTelemetry(analytics),
        )
    }

    private suspend fun DocumentDetailViewModel.content(): DocumentDetailContent =
        assertIs<Loadable.Ready<DocumentDetailContent>>(
            state.first { it.content is Loadable.Ready }.content,
        ).value

    @Test
    fun readsTheDocumentNamedBySavedState() = runTest(dispatcher) {
        val content = viewModel().content()

        assertEquals("d1", content.id.value)
        assertEquals(DocumentType.INSURANCE, content.type)
        assertTrue(content.isVerified, "a DigiLocker copy carries the badge")
        assertTrue(content.isFileAvailable)
    }

    @Test
    fun aMissingFile_isReportedAndShown() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val content = viewModel(files = FakeDocumentFileStore(), analytics = analytics).content()
        advanceUntilIdle()

        assertTrue(!content.isFileAvailable)
        assertTrue(analytics.names().isNotEmpty())
    }

    @Test
    fun openingADocument_isCounted() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        viewModel(analytics = analytics).content()
        advanceUntilIdle()

        val (_, properties) = analytics.events.first { it.first == DocumentVaultTelemetry.Event.DOCUMENT_OPENED }
        assertEquals(DocumentType.INSURANCE.name, properties[DocumentVaultTelemetry.Key.TYPE])
        assertEquals(true, properties[DocumentVaultTelemetry.Key.VERIFIED])
    }

    @Test
    fun deletingRemovesTheDocumentAndLeaves() = runTest(dispatcher) {
        val repository = FakeDocumentRepository(listOf(insurance))
        val files = FakeDocumentFileStore(stored = setOf(insurance.storagePath))
        val viewModel = viewModel(repository = repository, files = files)
        viewModel.content()

        viewModel.onEvent(DocumentDetailEvent.File.Delete)
        advanceUntilIdle()

        assertTrue(repository.observe(TEST_CAR).first().isEmpty())
        assertEquals(listOf(insurance.storagePath), files.deleted)
        assertEquals(DocumentDetailEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun deletingAsksToLeaveOnlyOnce() = runTest(dispatcher) {
        val repository = FakeDocumentRepository(listOf(insurance))
        val viewModel = viewModel(repository = repository, files = FakeDocumentFileStore(stored = setOf(insurance.storagePath)))
        viewModel.content()

        // Deleting gives the screen two reasons to leave: the delete succeeds, and the
        // document then stops arriving. Two pops would take the vault behind it as well.
        val leaves = mutableListOf<DocumentDetailEffect>()
        val collector = launch { viewModel.effects.toList(leaves) }

        viewModel.onEvent(DocumentDetailEvent.File.Delete)
        advanceUntilIdle()

        assertEquals(listOf<DocumentDetailEffect>(DocumentDetailEffect.NavigateBack), leaves.toList())
        collector.cancel()
    }

    @Test
    fun renewOpensTheAddFlowOnTheSameType() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(DocumentDetailEvent.Open.Renew)

        assertEquals(DocumentDetailEffect.OpenAdd(DocumentType.INSURANCE), viewModel.effects.first())
    }

    @Test
    fun shareCarriesTheDocumentId() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(DocumentDetailEvent.Open.Share)

        val effect = assertIs<DocumentDetailEffect.OpenShare>(viewModel.effects.first())
        assertEquals("d1", effect.id.value)
    }

    @Test
    fun viewHandsTheStoredPathToTheRoute() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(DocumentDetailEvent.File.View)

        val effect = assertIs<DocumentDetailEffect.OpenFile>(viewModel.effects.first())
        assertEquals(insurance.storagePath, effect.storagePath)
    }

    @Test
    fun savingACopyNamesItAfterTheDocument() = runTest(dispatcher) {
        val downloads = RecordingDownloads()
        val viewModel = viewModel(downloads = downloads)
        viewModel.content()

        viewModel.onEvent(DocumentDetailEvent.File.Download)
        advanceUntilIdle()

        // The stored key is an id nobody can read; what leaves the app is named after the
        // paper, and declared as what it actually is.
        val (storageKey, fileName, mimeType) = downloads.saved.single()
        assertEquals(insurance.storagePath, storageKey)
        assertEquals("Insurance.pdf", fileName)
        assertEquals("application/pdf", mimeType)
        assertEquals(DocumentDetailEffect.CopySaved, viewModel.effects.first())
    }

    @Test
    fun aCopyThatCouldNotBeSavedSaysSo() = runTest(dispatcher) {
        val viewModel = viewModel(downloads = RecordingDownloads(DomainError.PersistenceFailure("no space")))
        viewModel.content()

        viewModel.onEvent(DocumentDetailEvent.File.Download)
        advanceUntilIdle()

        assertEquals(DocumentDetailEffect.CopySaveFailed, viewModel.effects.first())
    }

    @Test
    fun replacingTheFileDropsTheVerifiedBadge() = runTest(dispatcher) {
        val repository = FakeDocumentRepository(listOf(insurance))
        val viewModel = viewModel(repository = repository)
        viewModel.content()

        viewModel.onEvent(DocumentDetailEvent.File.Replace("content://camera/rescan.jpg"))
        advanceUntilIdle()

        assertEquals(DocumentSource.UPLOADED, repository.observe(insurance.id).first()?.source)
    }
}
