package com.hopcape.odo.infrastructure.database.document

import com.hopcape.odo.infrastructure.database.db.Documents
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentMappersTest {

    /** Pinned so the created-at conversion is tested against a zone, not the machine's. */
    private val delhi = TimeZone.of("Asia/Kolkata")

    private fun row(
        docType: String = DocumentType.INSURANCE.name,
        docSource: String = DocumentSource.DIGILOCKER.name,
        title: String? = "SafeDrive comprehensive",
        issuedDate: String? = "2026-04-01",
        expiryDate: String? = "2027-03-31",
        createdAt: String = "2026-07-30T10:00:00Z",
    ) = Documents(
        id = "doc-1",
        car_id = "car-1",
        owner_id = "owner-1",
        doc_type = docType,
        title = title,
        storage_path = "documents/car-1/doc-1.pdf",
        doc_source = docSource,
        issued_date = issuedDate,
        expiry_date = expiryDate,
        created_at = createdAt,
        updated_at = "2026-07-30T10:00:00Z",
        deleted_at = null,
        remote_version = null,
        sync_status = SyncStatus.PENDING.name,
    )

    @Test
    fun toDomain_mapsEveryField() {
        val document = row().toDomain(delhi)

        assertEquals("doc-1", document.id.value)
        assertEquals("car-1", document.carId.value)
        assertEquals("owner-1", document.ownerId.value)
        assertEquals(DocumentType.INSURANCE, document.type)
        assertEquals("SafeDrive comprehensive", document.title?.value)
        assertEquals("documents/car-1/doc-1.pdf", document.storagePath)
        assertEquals(DocumentSource.DIGILOCKER, document.source)
        assertEquals(LocalDate(2026, 4, 1), document.issuedOn)
        assertEquals(LocalDate(2027, 3, 31), document.expiresOn)
        assertEquals(LocalDate(2026, 7, 30), document.addedOn)
    }

    @Test
    fun toDomain_readsAddedOnInTheOwnersZoneNotUtc() {
        // Stored at 20:30 UTC on the 29th, which is 2am on the 30th in Delhi. The timeline
        // has to date the document by the day the owner filed it.
        val document = row(createdAt = "2026-07-29T20:30:00Z").toDomain(delhi)

        assertEquals(LocalDate(2026, 7, 30), document.addedOn)
    }

    @Test
    fun toDomain_handlesNullOptionals() {
        val document = row(title = null, issuedDate = null, expiryDate = null).toDomain(delhi)

        assertNull(document.title)
        assertNull(document.issuedOn)
        assertNull(document.expiresOn)
    }

    @Test
    fun toDomain_readsUnknownTypeAsOther() {
        // A row written by a newer build. Filing it under the wrong heading is better than
        // a document the owner cannot open.
        assertEquals(DocumentType.OTHER, row(docType = "HOLOGRAM").toDomain(delhi).type)
    }

    @Test
    fun toDomain_readsUnknownSourceAsUploaded() {
        val document = row(docSource = "TELEPORTED").toDomain(delhi)

        assertEquals(DocumentSource.UPLOADED, document.source)
        // The point of the fallback: an unreadable source must never earn the badge.
        assertEquals(false, document.source.isVerified)
    }
}
