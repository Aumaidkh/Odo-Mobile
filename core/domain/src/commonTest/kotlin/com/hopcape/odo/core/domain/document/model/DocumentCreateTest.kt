package com.hopcape.odo.core.domain.document.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentCreateTest {

    private val today = LocalDate(2026, 7, 28)
    private val id = DocumentId("doc-1")
    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    private fun create(
        type: DocumentType = DocumentType.INSURANCE,
        storagePath: String? = "documents/car-1/doc-1.pdf",
        source: DocumentSource = DocumentSource.UPLOADED,
        title: String? = "SafeDrive comprehensive",
        issuedOn: LocalDate? = LocalDate(2026, 7, 4),
        expiresOn: LocalDate? = LocalDate(2027, 7, 3),
    ) = Document.create(
        id = id,
        ownerId = ownerId,
        carId = carId,
        type = type,
        storagePath = storagePath,
        source = source,
        today = today,
        title = title,
        issuedOn = issuedOn,
        expiresOn = expiresOn,
    )

    @Test
    fun validInput_buildsDocument() {
        val result = create()

        assertTrue(result.isRight(), "expected Right but was $result")
        val document = result.getOrNull()!!
        assertEquals(DocumentType.INSURANCE, document.type)
        assertEquals("documents/car-1/doc-1.pdf", document.storagePath)
        assertEquals("SafeDrive comprehensive", document.title?.value)
        assertEquals(DocumentSource.UPLOADED, document.source)
        assertEquals(LocalDate(2026, 7, 4), document.issuedOn)
        assertEquals(LocalDate(2027, 7, 3), document.expiresOn)
    }

    @Test
    fun optionalFields_mayAllBeAbsent() {
        val result = create(title = null, issuedOn = null, expiresOn = null)

        val document = result.getOrNull()!!
        assertNull(document.title)
        assertNull(document.issuedOn)
        assertNull(document.expiresOn)
        // No expiry is a lifetime paper, not a lapsed one.
        assertEquals(DocumentValidity.NoExpiry, document.validity(today))
    }

    @Test
    fun blankTitle_isAbsentNotAnError() {
        assertNull(create(title = "   ").getOrNull()!!.title)
    }

    @Test
    fun title_isTrimmed() {
        assertEquals("Policy 2026", create(title = "  Policy 2026  ").getOrNull()!!.title?.value)
    }

    @Test
    fun overLongTitle_isRejected() {
        val errors = create(title = "x".repeat(DocumentTitle.MAX_LENGTH + 1)).leftOrNull()!!
        assertEquals(DocumentTitle.MAX_LENGTH, assertIs<DomainError.DocumentTitleTooLong>(errors.head).max)
    }

    @Test
    fun missingStoragePath_isRejected() {
        assertTrue(create(storagePath = null).leftOrNull()!!.contains(DomainError.MissingDocumentFile))
    }

    @Test
    fun blankStoragePath_isRejected() {
        assertTrue(create(storagePath = "   ").leftOrNull()!!.contains(DomainError.MissingDocumentFile))
    }

    @Test
    fun storagePath_isTrimmed() {
        assertEquals("a/b.pdf", create(storagePath = "  a/b.pdf  ").getOrNull()!!.storagePath)
    }

    @Test
    fun futureIssueDate_isRejected() {
        val tomorrow = LocalDate(2026, 7, 29)
        assertTrue(create(issuedOn = tomorrow).leftOrNull()!!.contains(DomainError.IssueDateInFuture))
    }

    @Test
    fun issueDateOfToday_isAccepted() {
        assertTrue(create(issuedOn = today, expiresOn = null).isRight())
    }

    @Test
    fun expiryBeforeIssueDate_isRejected() {
        val result = create(issuedOn = LocalDate(2026, 7, 4), expiresOn = LocalDate(2026, 7, 3))
        assertTrue(result.leftOrNull()!!.contains(DomainError.ExpiryBeforeIssueDate))
    }

    @Test
    fun expiryInThePast_isAccepted_soLapsedPapersCanBeStored() {
        val result = create(issuedOn = LocalDate(2025, 7, 4), expiresOn = LocalDate(2026, 7, 3))

        assertTrue(result.isRight(), "storing a lapsed document is the renewal flow's whole point")
        val expired = assertIs<DocumentValidity.Expired>(result.getOrNull()!!.validity(today))
        assertEquals(25, expired.daysAgo)
    }

    @Test
    fun expiryWithoutIssueDate_isAccepted() {
        assertTrue(create(issuedOn = null, expiresOn = LocalDate(2020, 1, 1)).isRight())
    }

    @Test
    fun everyFailure_isReportedAtOnce() {
        val errors = create(
            storagePath = null,
            title = "x".repeat(DocumentTitle.MAX_LENGTH + 1),
            issuedOn = LocalDate(2026, 8, 1),
            expiresOn = LocalDate(2026, 7, 1),
        ).leftOrNull()!!

        assertEquals(4, errors.size, "expected all four field failures, got $errors")
        assertTrue(errors.contains(DomainError.MissingDocumentFile))
        assertTrue(errors.contains(DomainError.IssueDateInFuture))
        assertTrue(errors.contains(DomainError.ExpiryBeforeIssueDate))
        assertTrue(errors.any { it is DomainError.DocumentTitleTooLong })
    }

    @Test
    fun withFile_swapsPathAndSource_leavingClaimsUntouched() {
        val original = create(source = DocumentSource.DIGILOCKER).getOrNull()!!

        val replaced = original.withFile("documents/car-1/doc-1-rescan.jpg", DocumentSource.SCANNED)

        assertEquals("documents/car-1/doc-1-rescan.jpg", replaced.storagePath)
        assertEquals(DocumentSource.SCANNED, replaced.source)
        // A phone photo replacing an official copy must lose the Verified badge.
        assertTrue(original.source.isVerified)
        assertTrue(!replaced.source.isVerified)
        assertEquals(original.expiresOn, replaced.expiresOn)
        assertEquals(original.title?.value, replaced.title?.value)
        assertEquals(original.id, replaced.id)
    }
}
