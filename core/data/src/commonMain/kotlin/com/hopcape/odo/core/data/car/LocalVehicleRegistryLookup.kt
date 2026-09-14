package com.hopcape.odo.core.data.car

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.data.observability.DataTelemetry
import kotlin.coroutines.cancellation.CancellationException

/**
 * Answers a plate from the cars this device already holds.
 *
 * The first tier of the lookup chain, and the only one that works offline. An owner adding
 * a second car, or re-adding one they removed, gets the answer without a request.
 *
 * A miss is [DomainError.RegistrationNotFound] rather than an error: this tier having
 * nothing is the normal case, and the chain reads that as "ask the next one".
 */
internal class LocalVehicleRegistryLookup(
    private val cars: CarLocalDataSource,
    private val owners: CurrentOwnerProvider,
    private val telemetry: DataTelemetry,
) : VehicleRegistryLookup {

    override suspend fun lookup(
        registrationNumber: RegistrationNumber,
    ): Either<DomainError, RegisteredVehicle> {
        val vehicle = try {
            telemetry.span(ENTITY, OPERATION) {
                cars.vehicleByRegistration(owners.currentOwnerId(), registrationNumber)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // A read that fell over is not evidence the plate is unknown, so it must not
            // send the owner to manual entry with "we have no record of this plate".
            telemetry.crashed(ENTITY, OPERATION, throwable)
            return DomainError.LookupUnavailable.left()
        }
        return vehicle?.right() ?: DomainError.RegistrationNotFound.left()
    }

    private companion object {
        const val ENTITY = "car"
        const val OPERATION = "lookupByRegistration"
    }
}
