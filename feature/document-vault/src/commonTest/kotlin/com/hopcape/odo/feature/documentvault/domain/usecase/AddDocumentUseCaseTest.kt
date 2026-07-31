package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.entitlement.DocumentLimit
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentTitle
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.FakeDocumentFileStore
import com.hopcape.odo.feature.documentvault.FakeDocumentRepository
import com.hopcape.odo.feature.documentvault.FixedIdGenerator
import com.hopcape.odo.feature.documentvault.TEST_CAR
import com.hopcape.odo.feature.documentvault.TEST_CLOCK
import com.hopcape.odo.feature.documentvault.TEST_OWNER
import com.hopcape.odo.feature.documentvault.document
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AddDocumentUseCaseTest {

    private val newFileKey = "documents/car-1/doc-new.pdf"

    private val command = AddDocumentCommand(
        type = DocumentType.INSURANCE,
        pickedRef = "content://downloads/policy.pdf",
        source = DocumentSource.UPLOADED,
        title = "SafeDrive comprehensive",
        issuedOn = LocalDate(2026, 7, 4),
        expiresOn = LocalDate(2027, 7, 3),
    )

    private class Fixture(
        held: List<Document> = emptyList(),
        limit: DocumentLimit = DocumentLimit.UpTo(3),
        repositoryFailure: DomainError? = null,
        fileFailure: DomainError? = null,
    ) {
        val documents = FakeDocumentRepository(held, failWith = repositoryFailure)
        val files = FakeDocumentFileStore(failWith = fileFailure)
        val useCase = AddDocumentUseCase(
            documents = documents,
            files = files,
            allowance = DocumentAllowance { limit },
            idGenerator = FixedIdGenerator(),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        )
    }

    @Test
    fun storesTheFileAndTheRow() = runTest {
        val fixture = Fixture()

        val document = fixture.useCase(command, TEST_CAR, TEST_OWNER).getOrNull()!!

        assertEquals("doc-new", document.id.value)
        assertEquals(TEST_CAR, document.carId)
        assertEquals(TEST_OWNER, document.ownerId)
        assertEquals(newFileKey, document.storagePath)
        assertEquals(DocumentType.INSURANCE, document.type)
        assertEquals("SafeDrive comprehensive", document.title?.value)
        assertContains(fixture.files.saved, newFileKey)
        assertEquals(listOf(document.id), fixture.documents.observe(TEST_CAR).first().map { it.id })
    }

    @Test
    fun aFullPlanIsRejectedBeforeTheFileIsCopied() = runTest {
        val fixture = Fixture(
            held = listOf(
                document("d1", DocumentType.INSURANCE, LocalDate(2027, 1, 1)),
                document("d2", DocumentType.PUC, LocalDate(2027, 1, 1)),
                document("d3", DocumentType.RC, expiresOn = null),
            ),
            limit = DocumentLimit.UpTo(3),
        )

        val errors = fixture.useCase(command, TEST_CAR, TEST_OWNER).leftOrNull()!!

        assertEquals(3, assertIs<DomainError.DocumentLimitReached>(errors.head).limit)
        assertTrue(fixture.files.saved.isEmpty(), "no bytes should be copied for a rejected add")
        assertEquals(3, fixture.documents.observe(TEST_CAR).first().size)
    }

    @Test
    fun anUnlimitedPlanKeepsAccepting() = runTest {
        val fixture = Fixture(
            held = List(9) { document("d$it", DocumentType.OTHER, expiresOn = null) },
            limit = DocumentLimit.Unlimited,
        )

        assertTrue(fixture.useCase(command, TEST_CAR, TEST_OWNER).isRight())
    }

    @Test
    fun aFileThatCannotBeCopied_writesNoRow() = runTest {
        val fixture = Fixture(fileFailure = DomainError.PersistenceFailure("picker permission lapsed"))

        val errors = fixture.useCase(command, TEST_CAR, TEST_OWNER).leftOrNull()!!

        assertIs<DomainError.PersistenceFailure>(errors.head)
        assertTrue(fixture.documents.observe(TEST_CAR).first().isEmpty())
    }

    @Test
    fun invalidDetails_deleteTheCopiedFile() = runTest {
        val fixture = Fixture()

        val errors = fixture.useCase(
            command.copy(issuedOn = LocalDate(2027, 1, 1), expiresOn = LocalDate(2026, 1, 1)),
            TEST_CAR,
            TEST_OWNER,
        ).leftOrNull()!!

        assertContains(errors, DomainError.ExpiryBeforeIssueDate)
        assertTrue(fixture.files.saved.isEmpty(), "a rejected add must not leave bytes behind")
        assertEquals(listOf(newFileKey), fixture.files.deleted)
        assertTrue(fixture.documents.observe(TEST_CAR).first().isEmpty())
    }

    @Test
    fun everyFieldFailureIsReportedTogether() = runTest {
        val fixture = Fixture()

        val errors = fixture.useCase(
            command.copy(
                title = "x".repeat(DocumentTitle.MAX_LENGTH + 1),
                issuedOn = LocalDate(2026, 8, 1),
                expiresOn = LocalDate(2026, 1, 1),
            ),
            TEST_CAR,
            TEST_OWNER,
        ).leftOrNull()!!

        assertEquals(3, errors.size, "got $errors")
        assertContains(errors, DomainError.IssueDateInFuture)
        assertContains(errors, DomainError.ExpiryBeforeIssueDate)
        assertTrue(errors.any { it is DomainError.DocumentTitleTooLong })
    }

    @Test
    fun aFailedWrite_deletesTheCopiedFile() = runTest {
        val fixture = Fixture(repositoryFailure = DomainError.PersistenceFailure("disk full"))

        val errors = fixture.useCase(command, TEST_CAR, TEST_OWNER).leftOrNull()!!

        assertIs<DomainError.PersistenceFailure>(errors.head)
        assertEquals(listOf(newFileKey), fixture.files.deleted)
        assertTrue(fixture.files.saved.isEmpty())
    }
}
