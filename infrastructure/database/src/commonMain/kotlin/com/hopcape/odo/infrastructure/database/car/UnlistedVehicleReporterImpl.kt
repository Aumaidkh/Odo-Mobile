package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.car.VehicleCatalogSubmissionDto
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.catalog.UnlistedVehicleReporter
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * [UnlistedVehicleReporter] over [VehicleCatalogRemoteDataSource] — the client half of "my
 * car isn't listed".
 *
 * Silent on every failure path by contract: the car itself is already saved from whatever the
 * owner typed by the time this runs (garage/onboarding call it after a successful save, never
 * as a precondition of one), so nothing here may surface an error the owner would have to act
 * on. That includes the case a caller cannot tell apart from a real failure — signed out, or
 * a build with no Supabase configuration, where [VehicleCatalogRemoteDataSource] is the fake
 * that already no-ops [VehicleCatalogRemoteDataSource.submitUnlisted].
 */
internal class UnlistedVehicleReporterImpl(
    private val remote: VehicleCatalogRemoteDataSource,
    private val owner: CurrentOwnerProvider,
    private val telemetry: DataTelemetry,
    private val clock: Clock = Clock.System,
) : UnlistedVehicleReporter {

    override suspend fun report(make: String, model: String, variant: String?) {
        val ownerId = owner.currentOwnerId()
        // Not signed in yet — there is no account to attribute the submission to, and RLS
        // would refuse it anyway (`owner_id = auth.uid()`). Not a failure: an owner who adds
        // a car before signing in is Odo's normal offline-first path.
        if (ownerId == OwnerId.LOCAL_PLACEHOLDER) return

        telemetry.span(DataTelemetry.VEHICLE_CATALOG, OP_SUBMIT) {
            try {
                remote.submitUnlisted(
                    VehicleCatalogSubmissionDto(
                        ownerId = ownerId.value,
                        make = make,
                        model = model,
                        variant = variant,
                        createdAt = clock.now().toString(),
                    ),
                )
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
