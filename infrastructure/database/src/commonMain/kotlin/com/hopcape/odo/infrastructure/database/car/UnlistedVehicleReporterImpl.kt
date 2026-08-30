package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.catalog.UnlistedVehicleReporter
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * [UnlistedVehicleReporter] over the local `vehicle_catalog_submissions` outbox — the client
 * half of "my car isn't listed".
 *
 * **Writes locally, then asks for a sync — it never talks to the network itself.** It used
 * to call [com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource] directly from
 * whatever coroutine [report] was launched on, which was lost outright when the owner was
 * not signed in yet (RLS refuses an unauthenticated insert, and there was nowhere local to
 * hold the report for later) and was a race against the caller's scope being cancelled
 * otherwise (garage/onboarding call this right after a successful save, immediately before
 * navigating away). A local insert has neither problem: it is a fast suspend write that
 * completes before any navigation effect can cancel it, and it leaves the report exactly
 * like any other pre-auth write — under [CurrentOwnerProvider]'s placeholder, re-stamped by
 * sign-in adoption (SYNC_DESIGN §9) and pushed by [VehicleCatalogSubmissionSyncable] like
 * every other syncable table, no bespoke queue required.
 *
 * Silent on every failure path by contract: the car itself is already saved from whatever the
 * owner typed by the time this runs, so nothing here may surface an error the owner would
 * have to act on.
 */
internal class UnlistedVehicleReporterImpl(
    private val database: OdoDatabase,
    private val owner: CurrentOwnerProvider,
    private val idGenerator: IdGenerator,
    private val scheduler: SyncScheduler,
    private val telemetry: DataTelemetry,
    private val clock: Clock = Clock.System,
) : UnlistedVehicleReporter {

    override suspend fun report(make: String, model: String, variant: String?) {
        telemetry.span(DataTelemetry.VEHICLE_CATALOG, OP_SUBMIT) {
            try {
                database.vehicleCatalogSubmissionQueries.insertSubmission(
                    id = idGenerator.newId(),
                    ownerId = owner.currentOwnerId().value,
                    make = make,
                    model = model,
                    variant = variant,
                    now = clock.now().toString(),
                    syncStatus = SyncStatus.PENDING.name,
                )
                // A syncable record of its own asks for a push like any other local write.
                // Scheduling failure never fails the report — it is safely local and PENDING,
                // and the next sync pass (app foreground, another local write) picks it up.
                try {
                    scheduler.requestSync(SyncReason.LocalWrite)
                } catch (e: Exception) {
                    telemetry.crashed(DataTelemetry.VEHICLE_CATALOG, "$OP_SUBMIT.schedule", e)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.VEHICLE_CATALOG, OP_SUBMIT, e)
            }
        }
    }

    private companion object {
        const val OP_SUBMIT = "submitUnlisted"
    }
}
