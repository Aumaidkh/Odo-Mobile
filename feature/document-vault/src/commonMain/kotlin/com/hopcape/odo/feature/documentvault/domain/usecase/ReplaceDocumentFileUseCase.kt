package com.hopcape.odo.feature.documentvault.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import kotlinx.coroutines.flow.first

/**
 * Swaps the file behind a document, for example a clearer scan of the same paper.
 *
 * Only the file and its source change. The dates and title stay, because the document still
 * describes the same paper. Replacing an official DigiLocker copy with a phone photo drops
 * the Verified badge, which is why the source travels with the file.
 *
 * The old file is deleted only after the row is updated, and only if the new file went to a
 * different path. A replacement with the same extension overwrites the old file, so there
 * is nothing left to remove.
 */
internal class ReplaceDocumentFileUseCase(
    private val documents: DocumentRepository,
    private val files: DocumentFileStore,
) {
    suspend operator fun invoke(
        id: DocumentId,
        pickedRef: String,
        source: DocumentSource,
    ): Either<DomainError, Document> = either {
        val existing = documents.observe(id).first()
        ensureNotNull(existing) { DomainError.DocumentNotFound }

        val newPath = files.save(pickedRef, existing.carId, id).bind()
        val updated = documents.update(existing.withFile(newPath, source))
            .onLeft { files.delete(newPath) }
            .bind()

        if (existing.storagePath != newPath) files.delete(existing.storagePath)
        updated
    }
}
