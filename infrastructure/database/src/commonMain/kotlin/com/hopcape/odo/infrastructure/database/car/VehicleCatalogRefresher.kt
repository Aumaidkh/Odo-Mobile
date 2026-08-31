package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps the local `vehicle_make`/`vehicle_model` cache current with the shared Supabase
 * catalog, in the background.
 *
 * This is *not* part of the `Syncable`/`Synchronizer` engine (SYNC_DESIGN's per-owner
 * push/pull): `vehicle_makes`/`vehicle_models` carry no `owner_id` and every account reads the
 * same rows, so there is nothing to scope a pull to. It is a plain one-way refresh — fetch,
 * then replace — run once per process at startup and never awaited by anything, because the
 * picker has to stay usable from [VehicleCatalogImpl]'s local cache whether this succeeds,
 * fails, or (on a build with no Supabase configuration) fetches nothing at all.
 *
 * A full delete-and-reinsert, exactly like [seedVehicleReferenceData]'s own version-bump path
 * and for the same reason: `cars.make`/`cars.model` are plain strings with no foreign key into
 * these tables, so replacing every reference row can never touch a car someone has already
 * saved.
 */
internal class VehicleCatalogRefresher(
    private val database: OdoDatabase,
    private val remote: VehicleCatalogRemoteDataSource,
    private val telemetry: DataTelemetry,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Fire-and-forget: returns immediately, the pull (if any) runs on [scope]. */
    fun refreshInBackground() {
        scope.launch {
            telemetry.span(DataTelemetry.VEHICLE_CATALOG, OP_REFRESH) {
                try {
                    val makes = remote.fetchMakes()
                    val models = remote.fetchModels()
                    // Empty is the fake data source's honest answer on a build with no
                    // Supabase configuration, and a real server with a temporarily broken
                    // catalog for one that has one. Either way there is nothing worth
                    // wiping the local cache for.
                    if (makes.isEmpty()) return@span

                    database.transaction {
                        database.vehicleModelQueries.deleteAllModels()
                        database.vehicleMakeQueries.deleteAllMakes()
                        makes.forEach { make ->
                            database.vehicleMakeQueries.insertMake(
                                id = make.id,
                                name = make.name,
                                display_order = make.displayOrder,
                            )
                        }
                        models.forEach { model ->
                            database.vehicleModelQueries.insertModel(
                                id = model.id,
                                make_id = model.makeId,
                                name = model.name,
                                variant = model.variant,
                                display_order = model.displayOrder,
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    // The bundled/local-bootstrap catalog stays exactly as it was — a
                    // failed refresh is a missed opportunity, never a broken picker.
                    telemetry.crashed(DataTelemetry.VEHICLE_CATALOG, OP_REFRESH, e)
                }
            }
        }
    }

    private companion object {
        const val OP_REFRESH = "refresh"
    }
}
