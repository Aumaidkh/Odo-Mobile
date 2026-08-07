package com.hopcape.odo.infrastructure.database.document

import com.hopcape.odo.infrastructure.database.db.Documents
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * DB row → domain. The boundary where a generated [Documents] row becomes a [Document];
 * the domain never sees the row type. Rehydrates via `reconstitute` (trusted local data,
 * already validated when it was written).
 *
 * [zone] turns the stored `created_at` instant into the day the document was filed, in the
 * owner's zone rather than by truncating the UTC text — a document uploaded at 2am in Delhi
 * was stored as the previous evening in UTC. A parameter so tests can pin it.
 *
 * The remaining sync columns are read and ignored: they exist for the engine, and letting
 * them reach the domain is what the layering forbids.
 */
internal fun Documents.toDomain(zone: TimeZone = TimeZone.currentSystemDefault()): Document = Document.reconstitute(
    id = DocumentId(id),
    ownerId = OwnerId(owner_id),
    carId = CarId(car_id),
    type = doc_type.toDocumentType(),
    storagePath = storage_path,
    source = doc_source.toDocumentSource(),
    title = title,
    issuedOn = issued_date?.let { LocalDate.parse(it) },
    expiresOn = expiry_date?.let { LocalDate.parse(it) },
    addedOn = Instant.parse(created_at).toLocalDateTime(zone).date,
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
