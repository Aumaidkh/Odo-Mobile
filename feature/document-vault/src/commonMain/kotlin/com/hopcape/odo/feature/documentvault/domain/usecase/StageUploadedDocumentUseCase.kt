package com.hopcape.odo.feature.documentvault.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore

/**
 * Copies a picked file into app storage and answers with the key it was stored under.
 *
 * No row is written here, and that is the point. An uploaded paper now goes through the same
 * confirm step a photographed one does — the step that reads the expiry date off it — and
 * that step files the document. Writing the row here would put a paper in the vault that
 * nobody has read a date off, which is the state this exists to end.
 *
 * The plan is checked before the copy, so a large PDF is not copied only to be rejected.
 *
 * The file is named after a freshly generated id. That id is not the row's: the confirm step
 * generates its own, exactly as it already does for a photographed paper. The name only has
 * to be unique, and the row records whatever key it was given.
 *
 * A file staged for a confirm step the owner then abandons stays in app storage with no row
 * pointing at it. That costs disk, not correctness, and is the same trade the scanner already
 * makes with a photo taken and never saved.
 */
internal class StageUploadedDocumentUseCase(
    private val documents: DocumentRepository,
    private val files: DocumentFileStore,
    private val allowance: DocumentAllowance,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        pickedRef: String,
        carId: CarId,
        ownerId: OwnerId,
    ): Either<DomainError, String> = either {
        val limit = allowance.current()
        val held = documents.countForOwner(ownerId)
        ensure(limit.allows(held)) { DomainError.DocumentLimitReached(limit.cap ?: held) }

        files.save(
            pickedRef = pickedRef,
            carId = carId,
            documentId = DocumentId.new(idGenerator),
        ).bind()
    }
}
