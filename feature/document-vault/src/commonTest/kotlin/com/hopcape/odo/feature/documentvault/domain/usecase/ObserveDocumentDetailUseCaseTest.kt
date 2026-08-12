package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.feature.documentvault.FakeDocumentFileStore
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.document
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveDocumentDetailUseCaseTest {

    private val insurance = document(
        id = "d1",
        type = DocumentType.INSURANCE,
        issuedOn = LocalDate(2025, 8, 4),
        expiresOn = LocalDate(2026, 8, 4),
    )

    private fun useCase(
        vararg documents: Document,
        storedFiles: Set<String> = documents.map { it.storagePath }.toSet(),
    ) = ObserveDocumentDetailUseCase(
        documents = FakeDocumentRepository(documents.toList()),
        files = FakeDocumentFileStore(stored = storedFiles),
        clock = TEST_CLOCK,
        timeZone = TimeZone.UTC,
    )

    @Test
    fun resolvesTheDocumentAgainstToday() = runTest {
        val detail = assertNotNull(useCase(insurance).invoke(insurance.id).first())

        assertEquals(insurance.id, detail.document.id)
        val expiring = assertIs<DocumentValidity.ExpiringSoon>(detail.validity)
        assertEquals(7, expiring.daysLeft)
        assertTrue(detail.isFileAvailable)
    }

    @Test
    fun missingDocument_emitsNull_soTheScreenCanClose() = runTest {
        assertNull(useCase(insurance).invoke(DocumentId("gone")).first())
    }

    @Test
    fun progressIsTheFractionOfCoverAlreadyRun() = runTest {
        val detail = assertNotNull(useCase(insurance).invoke(insurance.id).first())

        // 358 of a 365-day policy elapsed on 28 Jul 2026.
        val progress = assertNotNull(detail.validityProgress)
        assertTrue(abs(progress - 358f / 365f) < 0.001f, "progress was $progress")
    }

    @Test
    fun withoutAnIssueDate_thereIsNoProgressToShow() = runTest {
        val noIssueDate = document("d2", DocumentType.PUC, expiresOn = LocalDate(2026, 11, 12))

        val detail = assertNotNull(useCase(noIssueDate).invoke(noIssueDate.id).first())

        assertNull(detail.validityProgress, "there is no start date to measure the bar from")
    }

    @Test
    fun aLifetimePaper_hasNoProgressAndNoNudge() = runTest {
        val rc = document("d3", DocumentType.RC, issuedOn = LocalDate(2020, 1, 1), expiresOn = null)

        val detail = assertNotNull(useCase(rc).invoke(rc.id).first())

        assertNull(detail.validityProgress)
        assertNull(detail.nextReminder)
        assertEquals(DocumentValidity.NoExpiry, detail.validity)
    }

    @Test
    fun progressIsClampedForALapsedPaper() = runTest {
        val lapsed = document(
            id = "d4",
            type = DocumentType.PUC,
            issuedOn = LocalDate(2025, 7, 3),
            expiresOn = LocalDate(2026, 1, 3),
        )

        val detail = assertNotNull(useCase(lapsed).invoke(lapsed.id).first())

        assertEquals(1f, detail.validityProgress)
    }

    @Test
    fun carriesTheNextNudge() = runTest {
        val detail = assertNotNull(useCase(insurance).invoke(insurance.id).first())

        assertEquals(7, detail.nextReminder?.daysBefore)
        assertEquals(LocalDate(2026, 7, 28), detail.nextReminder?.on)
    }

    @Test
    fun aFileLostSinceTheRowWasWritten_readsAsUnavailable() = runTest {
        val detail = assertNotNull(
            useCase(insurance, storedFiles = emptySet()).invoke(insurance.id).first(),
        )

        assertFalse(detail.isFileAvailable, "the screen must not offer to open a file that is gone")
    }

    @Test
    fun onlyAnOfficialCopyIsVerified() = runTest {
        val official = document(
            id = "d5",
            type = DocumentType.RC,
            expiresOn = null,
            source = DocumentSource.DIGILOCKER,
        )

        assertTrue(assertNotNull(useCase(official).invoke(official.id).first()).isVerified)
        assertFalse(assertNotNull(useCase(insurance).invoke(insurance.id).first()).isVerified)
    }
}
