package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.FakeDocumentFileStore
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.RecordingReminderScheduler
import com.hopcape.odo.feature.documentvault.TEST_CAR
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.document
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edit, replace-file and delete — the writes that act on a document already in the vault. */
class DocumentWriteUseCasesTest {

    private val stored = document(
        id = "d1",
        type = DocumentType.INSURANCE,
        issuedOn = LocalDate(2025, 8, 4),
        expiresOn = LocalDate(2026, 8, 4),
        source = DocumentSource.DIGILOCKER,
        storagePath = "documents/car-1/d1.pdf",
    )

    private fun repository(vararg documents: Document, failure: DomainError? = null) =
        FakeDocumentRepository(documents.toList(), failWith = failure)

    private val reminders = RecordingReminderScheduler()

    private fun update(documents: FakeDocumentRepository) = UpdateDocumentUseCase(
        documents = documents,
        reminders = reminders,
        clock = TEST_CLOCK,
        timeZone = TimeZone.UTC,
    )

    // --- edit details ---------------------------------------------------------------

    @Test
    fun editChangesTheDetailsAndKeepsTheFile() = runTest {
        val documents = repository(stored)

        val edited = update(documents)(
            stored.id,
            UpdateDocumentCommand(
                type = DocumentType.INSURANCE,
                title = "SafeDrive 2026",
                issuedOn = LocalDate(2025, 8, 4),
                expiresOn = LocalDate(2027, 8, 4),
            ),
        ).getOrNull()!!

        assertEquals("SafeDrive 2026", edited.title?.value)
        assertEquals(LocalDate(2027, 8, 4), edited.expiresOn)
        assertEquals(1, reminders.refreshes, "a new expiry means a new reminder day")
        assertEquals(stored.id, edited.id)
        assertEquals(stored.storagePath, edited.storagePath)
        assertEquals(DocumentSource.DIGILOCKER, edited.source, "an edit does not change where the file came from")
        assertEquals("SafeDrive 2026", documents.observe(stored.id).first()?.title?.value)
    }

    @Test
    fun editCanCorrectTheType() = runTest {
        val documents = repository(stored)

        val edited = update(documents)(
            stored.id,
            UpdateDocumentCommand(type = DocumentType.OTHER, expiresOn = stored.expiresOn),
        ).getOrNull()!!

        assertEquals(DocumentType.OTHER, edited.type)
    }

    @Test
    fun editIsCheckedByTheSameRulesAsAnAdd() = runTest {
        val documents = repository(stored)

        val errors = update(documents)(
            stored.id,
            UpdateDocumentCommand(
                type = DocumentType.INSURANCE,
                issuedOn = LocalDate(2026, 1, 1),
                expiresOn = LocalDate(2025, 1, 1),
            ),
        ).leftOrNull()!!

        assertContains(errors, DomainError.ExpiryBeforeIssueDate)
        assertEquals(stored.expiresOn, documents.observe(stored.id).first()?.expiresOn, "the row is untouched")
    }

    @Test
    fun editingSomethingThatIsGone_reportsNotFound() = runTest {
        val errors = update(repository())(
            DocumentId("gone"),
            UpdateDocumentCommand(type = DocumentType.RC),
        ).leftOrNull()!!

        assertEquals(DomainError.DocumentNotFound, errors.head)
    }

    // --- replace the file -----------------------------------------------------------

    @Test
    fun replacingTheFileKeepsWhatTheDocumentClaims() = runTest {
        val documents = repository(stored)
        val files = FakeDocumentFileStore(stored = setOf(stored.storagePath))

        val replaced = ReplaceDocumentFileUseCase(documents, files)(
            stored.id,
            pickedRef = "content://camera/rescan.jpg",
            source = DocumentSource.SCANNED,
        ).getOrNull()!!

        assertEquals(stored.expiresOn, replaced.expiresOn)
        assertEquals(stored.title?.value, replaced.title?.value)
        assertEquals(DocumentSource.SCANNED, replaced.source)
        assertTrue(!replaced.source.isVerified, "a phone photo replacing an official copy loses the badge")
    }

    @Test
    fun replacingWithTheSamePath_leavesTheOverwrittenFileAlone() = runTest {
        val documents = repository(stored)
        val files = FakeDocumentFileStore(stored = setOf(stored.storagePath))

        // The fake writes to the same key the document already has, as a same-extension
        // replacement does on a real device.
        ReplaceDocumentFileUseCase(documents, files)(
            stored.id,
            pickedRef = "content://downloads/policy-v2.pdf",
            source = DocumentSource.UPLOADED,
        )

        assertTrue(files.deleted.isEmpty(), "the new file overwrote the old one; deleting it would delete both")
        assertContains(files.saved, stored.storagePath)
    }

    @Test
    fun replacingWithADifferentPath_removesTheOldFile() = runTest {
        val jpgDocument = document(
            id = "d2",
            type = DocumentType.PUC,
            expiresOn = LocalDate(2026, 11, 12),
            storagePath = "documents/car-1/d2.jpg",
        )
        val documents = repository(jpgDocument)
        val files = FakeDocumentFileStore(stored = setOf(jpgDocument.storagePath))

        val replaced = ReplaceDocumentFileUseCase(documents, files)(
            jpgDocument.id,
            pickedRef = "content://downloads/puc.pdf",
            source = DocumentSource.UPLOADED,
        ).getOrNull()!!

        assertEquals("documents/car-1/d2.pdf", replaced.storagePath)
        assertEquals(listOf("documents/car-1/d2.jpg"), files.deleted)
    }

    @Test
    fun aFailedReplaceWrite_removesTheNewFile() = runTest {
        val documents = repository(stored, failure = DomainError.PersistenceFailure("disk full"))
        val files = FakeDocumentFileStore(stored = setOf(stored.storagePath))

        val error = ReplaceDocumentFileUseCase(documents, files)(
            stored.id,
            pickedRef = "content://camera/rescan.jpg",
            source = DocumentSource.SCANNED,
        ).leftOrNull()!!

        assertIs<DomainError.PersistenceFailure>(error)
        assertContains(files.deleted, stored.storagePath)
    }

    @Test
    fun replacingSomethingThatIsGone_reportsNotFound() = runTest {
        val files = FakeDocumentFileStore()

        val error = ReplaceDocumentFileUseCase(repository(), files)(
            DocumentId("gone"),
            pickedRef = "content://downloads/policy.pdf",
            source = DocumentSource.UPLOADED,
        ).leftOrNull()!!

        assertEquals(DomainError.DocumentNotFound, error)
        assertTrue(files.saved.isEmpty())
    }

    // --- delete ---------------------------------------------------------------------

    @Test
    fun deleteRemovesTheRowAndTheFile() = runTest {
        val documents = repository(stored)
        val files = FakeDocumentFileStore(stored = setOf(stored.storagePath))

        assertTrue(DeleteDocumentUseCase(documents, files, reminders)(stored.id).isRight())

        assertNull(documents.observe(stored.id).first())
        assertEquals(listOf(stored.storagePath), files.deleted)
        assertTrue(files.saved.isEmpty())
        assertEquals(1, reminders.refreshes, "a deleted document must stop nudging")
    }

    @Test
    fun aFailedDelete_keepsTheFile() = runTest {
        val documents = repository(stored, failure = DomainError.PersistenceFailure("locked"))
        val files = FakeDocumentFileStore(stored = setOf(stored.storagePath))

        val error = DeleteDocumentUseCase(documents, files, reminders)(stored.id).leftOrNull()!!

        assertIs<DomainError.PersistenceFailure>(error)
        assertTrue(files.deleted.isEmpty(), "the row is still there, so the file must be too")
    }

    @Test
    fun deletingSomethingThatIsGone_reportsNotFound() = runTest {
        val files = FakeDocumentFileStore()

        assertEquals(
            DomainError.DocumentNotFound,
            DeleteDocumentUseCase(repository(), files, reminders)(DocumentId("gone")).leftOrNull(),
        )
    }
}
