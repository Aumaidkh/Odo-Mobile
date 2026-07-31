package com.hopcape.odo.feature.documentvault.platform

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore

/**
 * iOS document storage — not built. The MVP is Android-only; the real store lands with the
 * iOS picker in Phase 2.
 *
 * It **refuses** rather than pretending to succeed. A store that answered with a key for
 * bytes it never wrote would hand the vault a document row pointing at nothing, and the
 * failure would surface later, as a paper that will not open, instead of here, where it is
 * obvious what is missing. The iOS file picker already returns nothing, so no real call can
 * reach this today — it exists so resolving the port on iOS fails honestly instead of
 * crashing on a missing definition.
 */
internal class IosDocumentFileStore : DocumentFileStore {

    override suspend fun save(
        pickedRef: String,
        carId: CarId,
        documentId: DocumentId,
    ): Either<DomainError, String> = DomainError.PersistenceFailure(NOT_IMPLEMENTED).left()

    /** Nothing was ever stored, so there is nothing to remove — a no-op that is true. */
    override suspend fun delete(storagePath: String) = Unit

    override suspend fun exists(storagePath: String): Boolean = false

    private companion object {
        const val NOT_IMPLEMENTED = "document storage is not implemented on iOS"
    }
}
