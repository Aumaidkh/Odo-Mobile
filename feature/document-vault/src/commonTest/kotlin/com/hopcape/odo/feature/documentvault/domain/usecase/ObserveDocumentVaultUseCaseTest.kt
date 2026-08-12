package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.TEST_CAR
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.document
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObserveDocumentVaultUseCaseTest {

    private fun useCase(vararg documents: Document) =
        ObserveDocumentVaultUseCase(
            documents = FakeDocumentRepository(documents.toList()),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        )

    private suspend fun snapshotOf(vararg documents: Document) =
        useCase(*documents).invoke(TEST_CAR).first()

    @Test
    fun emptyVault_isFourGaps() = runTest {
        val snapshot = snapshotOf()

        assertContentEquals(
            listOf(DocumentType.INSURANCE, DocumentType.PUC, DocumentType.RC, DocumentType.LICENCE),
            snapshot.slots.map { it.type },
        )
        assertTrue(snapshot.slots.all { it is VaultSlot.Missing })
        assertTrue(snapshot.isEmpty)
        assertFalse(snapshot.isFullyCovered)
    }

    @Test
    fun everyPaperOnFileAndInDate_readsAsCovered() = runTest {
        val snapshot = snapshotOf(
            document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
            document("d2", DocumentType.PUC, LocalDate(2026, 11, 12)),
            document("d3", DocumentType.RC, expiresOn = null),
            document("d4", DocumentType.LICENCE, LocalDate(2031, 8, 14)),
        )

        assertTrue(snapshot.isFullyCovered)
        assertEquals(4, snapshot.onFileCount)
        assertTrue(snapshot.needsAttention.isEmpty())
        assertTrue(snapshot.missing.isEmpty())
    }

    @Test
    fun aPaperInsideItsRenewalWindow_breaksCoverage() = runTest {
        val snapshot = snapshotOf(
            // 7 days out — inside the 30-day window.
            document("d1", DocumentType.INSURANCE, LocalDate(2026, 8, 4)),
            document("d2", DocumentType.PUC, LocalDate(2026, 11, 12)),
            document("d3", DocumentType.RC, expiresOn = null),
            document("d4", DocumentType.LICENCE, LocalDate(2031, 8, 14)),
        )

        assertFalse(snapshot.isFullyCovered, "a complete vault expiring next week is not covered")
        assertEquals(listOf(DocumentType.INSURANCE), snapshot.needsAttention.map { it.type })
    }

    @Test
    fun aCompleteVaultWithAGap_isNotCovered() = runTest {
        val snapshot = snapshotOf(document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)))

        assertFalse(snapshot.isFullyCovered)
        assertContentEquals(
            listOf(DocumentType.PUC, DocumentType.RC, DocumentType.LICENCE),
            snapshot.missing,
        )
    }

    @Test
    fun lapsedPaper_needsAttention() = runTest {
        val snapshot = snapshotOf(document("d1", DocumentType.PUC, LocalDate(2026, 7, 3)))

        val puc = assertIs<VaultSlot.OnFile>(snapshot.slots.first { it.type == DocumentType.PUC })
        assertIs<DocumentValidity.Expired>(puc.validity)
        assertTrue(puc.needsAttention)
        assertEquals(listOf(DocumentType.PUC), snapshot.needsAttention.map { it.type })
    }

    @Test
    fun twoPoliciesOfOneType_theLongestRunningWins() = runTest {
        val snapshot = snapshotOf(
            document("old", DocumentType.INSURANCE, LocalDate(2026, 7, 3)),
            document("new", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
        )

        val insurance = assertIs<VaultSlot.OnFile>(snapshot.slots.first { it.type == DocumentType.INSURANCE })
        assertEquals("new", insurance.document.id.value, "the later policy is the one still covering the owner")
        assertTrue(snapshot.needsAttention.isEmpty(), "last year's expired policy should not raise a warning")
    }

    @Test
    fun lifetimePaper_beatsAnExpiringOneOfTheSameType() = runTest {
        val snapshot = snapshotOf(
            document("dated", DocumentType.RC, LocalDate(2027, 1, 1)),
            document("lifetime", DocumentType.RC, expiresOn = null),
        )

        val rc = assertIs<VaultSlot.OnFile>(snapshot.slots.first { it.type == DocumentType.RC })
        assertEquals("lifetime", rc.document.id.value)
        assertEquals(DocumentValidity.NoExpiry, rc.validity)
    }

    @Test
    fun loanAndOtherPapers_areRowsNotGaps() = runTest {
        val snapshot = snapshotOf(
            document("loan", DocumentType.LOAN, expiresOn = null),
            document("misc", DocumentType.OTHER, expiresOn = null),
        )

        // The four tracked rows come first, then the documents the owner added.
        assertContentEquals(
            listOf(
                DocumentType.INSURANCE, DocumentType.PUC, DocumentType.RC, DocumentType.LICENCE,
                DocumentType.LOAN, DocumentType.OTHER,
            ),
            snapshot.slots.map { it.type },
        )
        assertEquals(2, snapshot.onFileCount)
        assertTrue(snapshot.missing.containsAll(listOf(DocumentType.INSURANCE, DocumentType.PUC)))
    }

    @Test
    fun severalLoanPapers_eachGetTheirOwnRow() = runTest {
        val snapshot = snapshotOf(
            document("loan-a", DocumentType.LOAN, LocalDate(2028, 1, 1)),
            document("loan-b", DocumentType.LOAN, LocalDate(2027, 1, 1)),
        )

        val loans = snapshot.slots.filterIsInstance<VaultSlot.OnFile>().filter { it.type == DocumentType.LOAN }
        assertContentEquals(listOf("loan-b", "loan-a"), loans.map { it.document.id.value })
    }

    @Test
    fun anExpiringPaper_carriesItsNextNudge() = runTest {
        // Insurance 8 days out: the 30-day nudge has passed, the 7-day one is tomorrow.
        val snapshot = snapshotOf(document("d1", DocumentType.INSURANCE, LocalDate(2026, 8, 5)))

        val insurance = assertIs<VaultSlot.OnFile>(snapshot.slots.first { it.type == DocumentType.INSURANCE })
        assertEquals(7, insurance.nextReminder?.daysBefore)
        assertEquals(LocalDate(2026, 7, 29), insurance.nextReminder?.on)
    }

    @Test
    fun aLifetimePaper_hasNoNudge() = runTest {
        val snapshot = snapshotOf(document("d1", DocumentType.RC, expiresOn = null))

        val rc = assertIs<VaultSlot.OnFile>(snapshot.slots.first { it.type == DocumentType.RC })
        assertEquals(null, rc.nextReminder)
    }

    @Test
    fun anotherCarsPapers_areNotThisCarsVault() = runTest {
        val snapshot = snapshotOf(
            document("mine", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
            document(
                "theirs",
                DocumentType.PUC,
                LocalDate(2027, 7, 3),
                carId = CarId("car-2"),
            ),
        )

        assertEquals(1, snapshot.onFileCount)
        assertTrue(snapshot.missing.contains(DocumentType.PUC))
    }

    @Test
    fun everyRowResolvesAgainstTheSameDay() = runTest {
        val snapshot = snapshotOf(document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)))

        assertEquals(LocalDate(2026, 7, 28), snapshot.today)
    }
}
