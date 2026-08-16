package com.hopcape.odo.feature.documentvault.presentation

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.feature.documentvault.FakeActiveCarProvider
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.RecordingAnalytics
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.document
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentVaultUseCase
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentRow
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultEffect
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultEvent
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultUiState
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultViewModel
import com.hopcape.odo.feature.documentvault.presentation.vault.VaultHeader
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
import kotlin.test.assertTrue
import com.hopcape.odo.core.domain.document.model.Document as DomainDocument

class DocumentVaultViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        vararg documents: DomainDocument,
        hasCar: Boolean = true,
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = DocumentVaultViewModel(
        activeCar = FakeActiveCarProvider(if (hasCar) com.hopcape.odo.feature.documentvault.TEST_CAR else null),
        observeVault = ObserveDocumentVaultUseCase(
            documents = FakeDocumentRepository(documents.toList()),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        ),
        showcase = ShowcaseArbiter(FakeShowcaseSeenStore()),
        telemetry = testTelemetry(analytics),
    )

    private class FakeShowcaseSeenStore : ShowcaseSeenStore {
        val seen = mutableSetOf<ShowcaseHookId>()
        override suspend fun isSeen(hook: ShowcaseHookId): Boolean = hook in seen
        override suspend fun markSeen(hook: ShowcaseHookId) {
            seen += hook
        }

        override suspend fun clearAll() = seen.clear()
    }

    @Test
    fun anEmptyVault_raisesTheRemindersShowcase() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertTrue(viewModel.state.first { it.vaultShowcase }.vaultShowcase)
    }

    @Test
    fun aFilledVault_neverRaisesTheRemindersShowcase() = runTest(dispatcher) {
        val viewModel = viewModel(
            document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
            document("d2", DocumentType.PUC, LocalDate(2026, 11, 12)),
        )
        viewModel.content()

        assertEquals(false, viewModel.state.value.vaultShowcase)
    }

    private suspend fun DocumentVaultViewModel.content() =
        assertIs<Loadable.Ready<*>>(state.first { it.content is Loadable.Ready }.content).value

    @Test
    fun mapsTheCarsDocumentsIntoRows() = runTest(dispatcher) {
        val viewModel = viewModel(
            document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
            document("d2", DocumentType.PUC, LocalDate(2026, 11, 12)),
        )

        val content = assertIs<com.hopcape.odo.feature.documentvault.presentation.vault.VaultContent>(viewModel.content())

        assertEquals(4, content.rows.size, "the four tracked types are always shown")
        val insurance = assertIs<DocumentRow.OnFile>(content.rows.first { it.type == DocumentType.INSURANCE })
        assertEquals(DocumentId("d1"), insurance.id)
        assertIs<DocumentValidity.Valid>(insurance.validity)
        assertIs<DocumentRow.Missing>(content.rows.first { it.type == DocumentType.RC })
    }

    @Test
    fun headerLeadsWithTheMostUrgentDocument() = runTest(dispatcher) {
        val viewModel = viewModel(
            // Expired outranks expiring, even though the PUC lapses sooner in days.
            document("d1", DocumentType.INSURANCE, LocalDate(2026, 8, 4)),
            document("d2", DocumentType.PUC, LocalDate(2026, 7, 3)),
        )

        val content = assertIs<com.hopcape.odo.feature.documentvault.presentation.vault.VaultContent>(viewModel.content())
        val header = assertIs<VaultHeader.NeedsAttention>(content.header)

        assertEquals(2, header.count)
        assertEquals(DocumentType.PUC, header.first)
    }

    @Test
    fun everythingInDateReadsAsCovered() = runTest(dispatcher) {
        val viewModel = viewModel(
            document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
            document("d2", DocumentType.PUC, LocalDate(2026, 11, 12)),
            document("d3", DocumentType.RC, expiresOn = null),
            document("d4", DocumentType.LICENCE, LocalDate(2031, 8, 14)),
        )

        val content = assertIs<com.hopcape.odo.feature.documentvault.presentation.vault.VaultContent>(viewModel.content())
        assertEquals(VaultHeader.Covered(count = 4), content.header)
    }

    @Test
    fun withoutACar_theScreenKeepsWaiting() = runTest(dispatcher) {
        val viewModel = viewModel(hasCar = false)
        advanceUntilIdle()

        assertEquals(DocumentVaultUiState(), viewModel.state.value)
    }

    @Test
    fun openingTheVault_reportsWhatItFound() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(
            document("d1", DocumentType.INSURANCE, LocalDate(2026, 8, 4)),
            analytics = analytics,
        )

        viewModel.content()
        advanceUntilIdle()

        val (name, properties) = analytics.events.first { it.first == DocumentVaultTelemetry.Event.VAULT_OPENED }
        assertEquals(DocumentVaultTelemetry.Event.VAULT_OPENED, name)
        assertEquals(1, properties[DocumentVaultTelemetry.Key.ON_FILE_COUNT])
        assertEquals(3, properties[DocumentVaultTelemetry.Key.MISSING_COUNT])
        assertEquals(1, properties[DocumentVaultTelemetry.Key.ATTENTION_COUNT])
    }

    @Test
    fun tappingADocument_asksTheRouteToOpenIt() = runTest(dispatcher) {
        val viewModel = viewModel(document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)))

        viewModel.onEvent(DocumentVaultEvent.DocumentTapped(DocumentId("d1")))

        assertEquals(DocumentVaultEffect.OpenDocument(DocumentId("d1")), viewModel.effects.first())
    }

    @Test
    fun addOnAMissingRow_carriesItsType() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(DocumentVaultEvent.AddTapped(DocumentType.PUC))

        assertEquals(DocumentVaultEffect.OpenAdd(DocumentType.PUC), viewModel.effects.first())
    }

    @Test
    fun theBottomBarAddNamesNoType() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(DocumentVaultEvent.AddAnyTapped)

        val effect = assertIs<DocumentVaultEffect.OpenAdd>(viewModel.effects.first())
        assertTrue(effect.prefillType == null)
    }
}
