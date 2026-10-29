package com.hopcape.odo.core.domain.setup

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/** The single device-local [SetupDraft]. Never synced. */
interface SetupDraftRepository {

    /** The draft, or null when there is none. */
    fun observe(): Flow<SetupDraft?>

    /** Replaces any draft with [draft]. */
    suspend fun save(draft: SetupDraft): Either<DomainError, Unit>

    suspend fun clear(): Either<DomainError, Unit>
}
