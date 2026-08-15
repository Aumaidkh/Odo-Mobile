package com.hopcape.odo.feature.documentvault.presentation

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.FakeDocumentFileStore
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.FakePlatformFileStore
import com.hopcape.odo.feature.documentvault.RecordingAnalytics
import com.hopcape.odo.feature.documentvault.RecordingDownloads
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.document
import com.hopcape.odo.feature.documentvault.domain.usecase.ExportDocumentFileUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentEffect
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentEvent
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentUiState
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentViewModel
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The share sheet's two actions. Both used to be stubs in the route, so what is asserted
 * here is that each one reaches the platform with the right file — the failure the owner
 * saw was a button that did nothing at all.
 */
class ShareDocumentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val insurance = document(
        id = "d1",
        type = DocumentType.INSURANCE,
        expiresOn = LocalDate(2026, 8, 4),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        exportStore: FakePlatformFileStore = FakePlatformFileStore(stored = setOf(insurance.storagePath)),
        downloads: RecordingDownloads = RecordingDownloads(),
    ): ShareDocumentViewModel {
        val repository = FakeDocumentRepository(listOf(insurance))
        val observeDetail = ObserveDocumentDetailUseCase(
            documents = repository,
            files = FakeDocumentFileStore(stored = setOf(insurance.storagePath)),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        )
        return ShareDocumentViewModel(
            documentId = DocumentId("d1"),
            observeDetail = observeDetail,
            exportFile = ExportDocumentFileUseCase(exportStore),
            downloads = downloads,
            telemetry = testTelemetry(RecordingAnalytics()),
        )
    }

    private suspend fun ShareDocumentViewModel.loaded(): ShareDocumentUiState =
        assertNotNull(state.first { it != null })

    @Test
    fun sharingExportsACopyTheOtherAppIsAllowedToRead() = runTest(dispatcher) {
        val exportStore = FakePlatformFileStore(stored = setOf(insurance.storagePath))
        val viewModel = viewModel(exportStore = exportStore)
        viewModel.loaded()

        viewModel.onEvent(ShareDocumentEvent.ShareTapped)
        advanceUntilIdle()

        // Out of the vault's own directory, which no other app can read, and into the one
        // the app exports — under the document's name rather than its id.
        assertEquals(listOf("exports/car-1/Insurance.pdf"), exportStore.written)
        val effect = assertIs<ShareDocumentEffect.ShareFile>(viewModel.effects.first())
        assertEquals("exports/car-1/Insurance.pdf", effect.storageKey)
        assertEquals("application/pdf", effect.mimeType)
    }

    @Test
    fun anExportThatFailsSaysSoInsteadOfSharingNothing() = runTest(dispatcher) {
        val viewModel = viewModel(
            exportStore = FakePlatformFileStore(failWith = DomainError.PersistenceFailure("no space")),
        )
        val loaded = viewModel.loaded()
        assertNull(loaded.notice)

        viewModel.onEvent(ShareDocumentEvent.ShareTapped)
        advanceUntilIdle()

        assertNotNull(assertNotNull(viewModel.state.value).notice)
    }

    @Test
    fun savingACopyReportsItselfOnTheSheet() = runTest(dispatcher) {
        val downloads = RecordingDownloads()
        val viewModel = viewModel(downloads = downloads)
        viewModel.loaded()

        viewModel.onEvent(ShareDocumentEvent.DownloadTapped)
        advanceUntilIdle()

        val (storageKey, fileName, mimeType) = downloads.saved.single()
        assertEquals(insurance.storagePath, storageKey)
        assertEquals("Insurance.pdf", fileName)
        assertEquals("application/pdf", mimeType)
        // Nothing opens, so the sheet is the only place the owner can be told it worked.
        assertNotNull(assertNotNull(viewModel.state.value).notice)
    }

    @Test
    fun aSaveThatFailsIsNotReportedAsASave() = runTest(dispatcher) {
        val downloads = RecordingDownloads(DomainError.PersistenceFailure("no space"))
        val viewModel = viewModel(downloads = downloads)
        viewModel.loaded()

        viewModel.onEvent(ShareDocumentEvent.DownloadTapped)
        advanceUntilIdle()

        assertTrue(downloads.saved.isEmpty())
        assertNotNull(assertNotNull(viewModel.state.value).notice)
    }
}
