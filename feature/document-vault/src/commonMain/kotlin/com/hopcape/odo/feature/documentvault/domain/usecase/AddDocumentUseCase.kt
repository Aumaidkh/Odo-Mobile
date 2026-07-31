package com.hopcape.odo.feature.documentvault.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Adds a document to the vault: checks the owner's plan, copies the file into app storage,
 * validates the details, and writes the row.
 *
 * The plan is checked first so a large PDF is not copied only to be rejected. If anything
 * after the copy fails, the copied file is deleted, so a rejected add never leaves bytes
 * behind with no row pointing at them.
 *
 * Field failures come back together via [EitherNel], so a review-and-confirm screen can
 * show them all at once.
 */
internal class AddDocumentUseCase(
    private val documents: DocumentRepository,
    private val files: DocumentFileStore,
    private val allowance: DocumentAllowance,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        command: AddDocumentCommand,
        carId: CarId,
        ownerId: OwnerId,
    ): EitherNel<DomainError, Document> = either {
        val limit = allowance.current()
        val held = documents.countForOwner(ownerId)
        ensure(limit.allows(held)) {
            nonEmptyListOf(DomainError.DocumentLimitReached(limit.cap ?: held))
        }

        val id = DocumentId.new(idGenerator)
        val storagePath = files.save(command.pickedRef, carId, id)
            .mapLeft { nonEmptyListOf(it) }
            .bind()

        val document = Document.create(
            id = id,
            ownerId = ownerId,
            carId = carId,
            type = command.type,
            storagePath = storagePath,
            source = command.source,
            today = clock.now().toLocalDateTime(timeZone).date,
            title = command.title,
            issuedOn = command.issuedOn,
            expiresOn = command.expiresOn,
        ).onLeft { files.delete(storagePath) }.bind()

        documents.add(document)
            .mapLeft { nonEmptyListOf(it) }
            .onLeft { files.delete(storagePath) }
            .bind()
    }
}
