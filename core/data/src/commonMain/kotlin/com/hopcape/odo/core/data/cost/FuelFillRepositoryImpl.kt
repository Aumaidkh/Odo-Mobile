package com.hopcape.odo.core.data.cost

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlin.time.Clock

/**
 * SQLDelight-backed [FuelFillRepository] — offline-first, like every other repository here.
 * The local DB is the source of truth and writes land `PENDING`.
 *
 * Deliberately **not** `Syncable` yet. There is no `fuel_fills` table on the server, so a
 * sync pass would post to nothing and turn every fill into a permanent failure. The rows
 * carry the full sync column set and sit `PENDING`, which is the honest state for data that
 * has nowhere to go — when the server table lands, this class gains a `SyncRunner` and the
 * rows already waiting are pushed with no migration.
 *
 * The port it implements has one method, so this does too. It still asks the scheduler for a sync after a write. The scheduler is free to find
 * nothing to do for this entity; what it must not do is stay unaware that the owner changed
 * something, and the call site is the part that gets forgotten later.
 */
internal class FuelFillRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val clock: Clock = Clock.System,
) : FuelFillRepository {

    private val queries get() = database.fuelFillQueries

    override suspend fun add(fill: FuelFill): Either<DomainError, FuelFill> =
        telemetry.span(DataTelemetry.FUEL_FILL, OP_ADD, fill.id.value) {
            try {
                val now = clock.now().toString()
                queries.insertFuelFill(
                    id = fill.id.value,
                    carId = fill.carId.value,
                    ownerId = fill.ownerId.value,
                    filledOn = fill.filledOn.toString(),
                    odometerKm = fill.odometer.km.toLong(),
                    quantityMilli = fill.quantityMilli,
                    fuelUnit = fill.unit.name,
                    amountPaise = fill.amount.paise,
                    stationName = fill.stationName,
                    paidVia = fill.paidVia.name,
                    transactionRef = fill.transactionRef,
                    now = now,
                    syncStatus = SyncStatus.PENDING.name,
                )
                requestSync(OP_ADD, fill.id.value)
                fill.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.FUEL_FILL, OP_ADD, e, fill.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }


    /**
     * Tell the scheduler there is something worth pushing, after the write has committed and
     * only when it succeeded. A scheduling failure never fails the write: the data is local
     * and `PENDING`, and the next trigger carries it.
     */
    private suspend fun requestSync(operation: String, id: String) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.FUEL_FILL, "$operation.schedule", e, id)
        }
    }

    private companion object {
        const val OP_ADD = "addFuelFill"
    }
}
