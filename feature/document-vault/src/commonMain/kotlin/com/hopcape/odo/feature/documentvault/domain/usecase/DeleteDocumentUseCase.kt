package com.hopcape.odo.feature.documentvault.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import kotlinx.coroutines.flow.first

/**
 * Removes a document from the vault.
 *
 * The row is soft-deleted, so the deletion can reach the owner's other devices. The file is
 * deleted for real: the owner asked for the document to go, and keeping a copy of their
 * insurance scan on disk after that would be wrong.
 *
 * The row goes first. If that fails, the file is still there and nothing is lost. If the
 * file delete fails afterwards, an unreferenced file is left behind, which wastes space but
 * breaks nothing.
 */
internal class DeleteDocumentUseCase(
    private val documents: DocumentRepository,
    private val files: DocumentFileStore,
) {
    suspend operator fun invoke(id: DocumentId): Either<DomainError, Unit> = either {
        val existing = documents.observe(id).first()
        ensureNotNull(existing) { DomainError.DocumentNotFound }

        documents.softDelete(id).bind()
        files.delete(existing.storagePath)
    }
}
