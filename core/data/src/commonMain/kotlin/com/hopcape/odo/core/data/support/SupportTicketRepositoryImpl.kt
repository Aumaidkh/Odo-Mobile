package com.hopcape.odo.core.data.support

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.support.SupportTicket
import com.hopcape.odo.core.domain.support.SupportTicketRepository
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

/**
 * [SupportTicketRepository] over the local database. Offline-first: the row lands `PENDING`
 * and a sync pass carries it, so a report written with no signal is a report.
 *
 * The owner id is read at write time rather than injected once, so a ticket filed before
 * sign-in is stamped with the offline placeholder and moved across by adoption later
 * (SYNC_DESIGN §9).
 */
internal class SupportTicketRepositoryImpl(
    private val local: SupportTicketLocalDataSource,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
) : SupportTicketRepository {

    override suspend fun submit(ticket: SupportTicket): Either<DomainError, SupportTicket> =
        telemetry.span(DataTelemetry.SUPPORT_TICKET, OP_SUBMIT, ticket.kind.name) {
            try {
                local.insert(currentOwner.currentOwnerId(), ticket)
                // Asked for, not waited on. The row is already durable; this only decides how
                // soon it leaves, and a device with no connection still keeps the ticket.
                scheduler.requestSync(SyncReason.LocalWrite)
                ticket.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SUPPORT_TICKET, OP_SUBMIT, e, ticket.kind.name)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    /**
     * A read failure becomes an empty list rather than a broken stream. Nothing a caller does
     * with a list of their own past tickets can act on the exception.
     */
    override fun observe(): Flow<List<SupportTicket>> = flow {
        emitAll(local.observe(currentOwner.currentOwnerId()))
    }.catch { e ->
        telemetry.crashed(DataTelemetry.SUPPORT_TICKET, OP_OBSERVE, e)
        emit(emptyList())
    }

    private companion object {
        const val OP_SUBMIT = "submit"
        const val OP_OBSERVE = "observe"
    }
}
