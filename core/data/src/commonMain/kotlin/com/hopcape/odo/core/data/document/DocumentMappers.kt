package com.hopcape.odo.core.data.document

import com.hopcape.odo.core.data.db.Documents
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate

/**
 * DB row → domain. The boundary where a generated [Documents] row becomes a [Document];
 * the domain never sees the row type. Rehydrates via `reconstitute` (trusted local data,
 * already validated when it was written).
 *
 * The sync columns are read and ignored: they exist for the engine, and letting them reach
 * the domain is what the layering forbids.
 */
internal fun Documents.toDomain(): Document = Document.reconstitute(
    id = DocumentId(id),
    ownerId = OwnerId(owner_id),
    carId = CarId(car_id),
    type = doc_type.toDocumentType(),
    storagePath = storage_path,
    source = doc_source.toDocumentSource(),
    title = title,
    issuedOn = issued_date?.let { LocalDate.parse(it) },
    expiresOn = expiry_date?.let { LocalDate.parse(it) },
)

/**
 * A type this build does not know reads as [DocumentType.OTHER] rather than throwing. The
 * row was written by a newer build, and an unopenable document is worse than one filed
 * under the wrong heading — the file, the expiry and the reminders all still work.
 */
private fun String.toDocumentType(): DocumentType =
    DocumentType.entries.firstOrNull { it.name == this } ?: DocumentType.OTHER

/**
 * An unrecognised source reads as [DocumentSource.UPLOADED] — the least it could be.
 *
 * The fallback is deliberately not [DocumentSource.DIGILOCKER]: that value is the only
 * thing granting the Verified badge, so guessing it would let a value this build cannot
 * understand claim the document came from the issuer.
 */
private fun String.toDocumentSource(): DocumentSource =
    DocumentSource.entries.firstOrNull { it.name == this } ?: DocumentSource.UPLOADED
