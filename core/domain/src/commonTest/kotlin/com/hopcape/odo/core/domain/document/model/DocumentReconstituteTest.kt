package com.hopcape.odo.core.domain.document.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DocumentReconstituteTest {

    private val id = DocumentId("doc-1")
    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    @Test
    fun createThenReconstitute_preservesEveryField() {
        val created = Document.create(
            id = id,
            ownerId = ownerId,
            carId = carId,
            type = DocumentType.PUC,
            storagePath = "documents/car-1/doc-1.jpg",
            source = DocumentSource.SCANNED,
            today = LocalDate(2026, 7, 28),
            title = "PUC — Andheri centre",
            issuedOn = LocalDate(2026, 5, 12),
            expiresOn = LocalDate(2026, 11, 12),
        ).getOrNull()!!

        // Rehydrate from the normalized/primitive values a DB row would carry.
        val rehydrated = Document.reconstitute(
            id = created.id,
            ownerId = created.ownerId,
            carId = created.carId,
            type = created.type,
            storagePath = created.storagePath,
            source = created.source,
            title = created.title?.value,
            issuedOn = created.issuedOn,
            expiresOn = created.expiresOn,
        )

        assertEquals(created.id, rehydrated.id)
        assertEquals(created.ownerId, rehydrated.ownerId)
        assertEquals(created.carId, rehydrated.carId)
        assertEquals(created.type, rehydrated.type)
        assertEquals(created.storagePath, rehydrated.storagePath)
        assertEquals(created.source, rehydrated.source)
        assertEquals(created.title?.value, rehydrated.title?.value)
        assertEquals(created.issuedOn, rehydrated.issuedOn)
        assertEquals(created.expiresOn, rehydrated.expiresOn)
    }

    @Test
    fun absentOptionals_rehydrateAsAbsent() {
        val rehydrated = Document.reconstitute(
            id = id,
            ownerId = ownerId,
            carId = carId,
            type = DocumentType.RC,
            storagePath = "documents/car-1/rc.pdf",
            source = DocumentSource.DIGILOCKER,
        )

        assertNull(rehydrated.title)
        assertNull(rehydrated.issuedOn)
        assertNull(rehydrated.expiresOn)
    }

    @Test
    fun corruptTitle_failsFast() {
        assertFailsWith<IllegalStateException> {
            Document.reconstitute(
                id = id,
                ownerId = ownerId,
                carId = carId,
                type = DocumentType.INSURANCE,
                storagePath = "documents/car-1/doc-1.pdf",
                source = DocumentSource.UPLOADED,
                title = "x".repeat(DocumentTitle.MAX_LENGTH + 1),
            )
        }
    }

    @Test
    fun corruptStoragePath_failsFast() {
        assertFailsWith<IllegalStateException> {
            Document.reconstitute(
                id = id,
                ownerId = ownerId,
                carId = carId,
                type = DocumentType.INSURANCE,
                storagePath = "  ",
                source = DocumentSource.UPLOADED,
            )
        }
    }
}
