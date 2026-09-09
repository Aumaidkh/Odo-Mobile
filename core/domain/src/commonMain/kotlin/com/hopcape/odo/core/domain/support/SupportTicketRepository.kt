package com.hopcape.odo.core.domain.support

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Where a submission is kept.
 *
 * **Saving is not sending.** The contract is that the row is on the device when this returns,
 * and reaching the server is the sync engine's errand afterwards — so a report written on a
 * train is a report, and the screen that confirms it is telling the truth.
 */
interface SupportTicketRepository {

    /** Save [ticket] locally. It reaches the server when there is a connection. */
    suspend fun submit(ticket: SupportTicket): Either<DomainError, SupportTicket>

    /** Everything this owner has sent, newest first. What the panel answers, they can see. */
    fun observe(): Flow<List<SupportTicket>>
}
