package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider

/**
 * Build the plate lookup: the cars on this device first, then [laterTiers] in order.
 *
 * The one public thing in this file. The tiers themselves stay `internal`, so a module that
 * binds this names a function rather than reaching for two classes it has no other business
 * knowing.
 *
 * The local tier is always first and is not optional: it is the only one that answers
 * offline, and it answers about cars the owner entered themselves. What follows it depends
 * on the build — the Supabase adapters where there are credentials, the hardcoded stub
 * where there are not.
 */
fun vehicleRegistryLookup(
    cars: CarLocalDataSource,
    owners: CurrentOwnerProvider,
    telemetry: DataTelemetry,
    laterTiers: List<VehicleRegistryLookup> = emptyList(),
): VehicleRegistryLookup = ChainedVehicleRegistryLookup(
    tiers = buildList {
        add(LocalVehicleRegistryLookup(cars = cars, owners = owners, telemetry = telemetry))
        addAll(laterTiers)
    },
)
