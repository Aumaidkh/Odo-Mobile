package com.hopcape.odo.infrastructure.database.city

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.city.UnlistedCityReporter
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * [UnlistedCityReporter] over the local `city_submissions` outbox — the client half of
 * "my city isn't listed".
 *
 * **Writes locally, then asks for a sync — it never talks to the network itself**, the same
 * shape [com.hopcape.odo.infrastructure.database.car.UnlistedVehicleReporterImpl] uses and for
 * the same reason: a local insert is a fast suspend write that completes before any navigation
 * effect can cancel it, and it leaves the report exactly like any other pre-auth write — under
 * [CurrentOwnerProvider]'s placeholder, re-stamped by sign-in adoption (SYNC_DESIGN §9) and
 * pushed by [CitySubmissionSyncable] like every other syncable table.
 *
 * Silent on every failure path by contract: the profile itself is already saved from whatever
 * the owner typed by the time this runs, so nothing here may surface an error the owner would
 * have to act on.
 */
internal class UnlistedCityReporterImpl(
    private val database: OdoDatabase,
    private val owner: CurrentOwnerProvider,
    private val idGenerator: IdGenerator,
    private val scheduler: SyncScheduler,
    private val telemetry: DataTelemetry,
    private val clock: Clock = Clock.System,
) : UnlistedCityReporter {

    override suspend fun report(name: String) {
        telemetry.span(DataTelemetry.CITY_CATALOG, OP_SUBMIT) {
            try {
                database.citySubmissionQueries.insertSubmission(
                    id = idGenerator.newId(),
                    ownerId = owner.currentOwnerId().value,
                    name = name,
                    now = clock.now().toString(),
                    syncStatus = SyncStatus.PENDING.name,
                )
                // A syncable record of its own asks for a push like any other local write.
                // Scheduling failure never fails the report — it is safely local and PENDING,
                // and the next sync pass (app foreground, another local write) picks it up.
                try {
                    scheduler.requestSync(SyncReason.LocalWrite)
                } catch (e: Exception) {
                    telemetry.crashed(DataTelemetry.CITY_CATALOG, "$OP_SUBMIT.schedule", e)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CITY_CATALOG, OP_SUBMIT, e)
            }
        }
    }

    private companion object {
        const val OP_SUBMIT = "submitUnlisted"
    }
}
