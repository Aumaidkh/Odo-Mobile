package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Files a scanned paper in the document vault.
 *
 * Written against the shared [DocumentRepository], **not** by calling the document-vault
 * feature: a `:feature:*` module never imports another one, so the scanner writes its own
 * use case against the same port. The two are not duplicates — the vault's add copies a
 * picked file into storage first, while here the photo is already in app storage because the
 * camera put it there.
 *
 * The free-tier cap is checked for the same reason the vault checks it: the limit is sold per
 * owner, and a document added through the scanner counts exactly as much as one uploaded.
 *
 * [DocumentSource.SCANNED] is stamped rather than passed in. A photograph is the owner's own
 * copy, so it can never earn the Verified badge, and making that a parameter would be leaving
 * a way to claim otherwise.
 */
internal class SaveScannedDocumentUseCase(
    private val documents: DocumentRepository,
    private val allowance: DocumentAllowance,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        command: SaveScannedDocumentCommand,
        carId: CarId,
        ownerId: OwnerId,
    ): EitherNel<DomainError, Document> = either {
        val limit = allowance.current()
        val held = documents.countForOwner(ownerId)
        ensure(limit.allows(held)) {
            nonEmptyListOf(DomainError.DocumentLimitReached(limit.cap ?: held))
        }

        val document = Document.create(
            id = DocumentId(ids.newId()),
            ownerId = ownerId,
            carId = carId,
            type = command.type,
            storagePath = command.photoStorageKey,
            source = DocumentSource.SCANNED,
            today = clock.now().toLocalDateTime(timeZone).date,
            title = command.title,
            issuedOn = command.issuedOn,
            expiresOn = command.expiresOn,
        ).bind()

        documents.add(document).mapLeft { nonEmptyListOf(it) }.bind()
    }
}

/**
 * The reviewed document, ready to be filed.
 *
 * [expiresOn] is nullable because some papers genuinely never lapse, not because it is
 * optional to ask. The review step insists on it for the types that do expire — a vault entry
 * with no expiry cannot produce a reminder, which is most of what the vault is for.
 */
internal data class SaveScannedDocumentCommand(
    val type: DocumentType,
    val photoStorageKey: String,
    val title: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
)
