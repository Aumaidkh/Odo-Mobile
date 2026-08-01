package com.hopcape.odo.core.platform.file

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * iOS file storage — not built. The MVP is Android-only; the real store lands with the iOS
 * picker in Phase 2.
 *
 * It **refuses** rather than pretending to succeed. A store that answered with a key for
 * bytes it never wrote would hand a feature a row pointing at nothing, and the failure would
 * surface later, as a paper that will not open, instead of here, where it is obvious what is
 * missing. The iOS picker already returns nothing, so no real call can reach this today — it
 * exists so resolving the port on iOS fails honestly instead of crashing on a missing
 * definition.
 */
internal class IosFileStore : PlatformFileStore {

    override suspend fun save(
        pickedRef: String,
        directory: String,
        fileName: String,
    ): Either<DomainError, String> = DomainError.PersistenceFailure(NOT_IMPLEMENTED).left()

    /** Nothing was ever stored, so there is nothing to remove — a no-op that is true. */
    override suspend fun delete(storageKey: String) = Unit

    override suspend fun exists(storageKey: String): Boolean = false

    private companion object {
        const val NOT_IMPLEMENTED = "file storage is not implemented on iOS"
    }
}
