package com.hopcape.odo.core.data

import com.hopcape.odo.core.data.car.CarRepositoryImpl
import com.hopcape.odo.core.data.car.StubVehicleRegistryLookup
import com.hopcape.odo.core.data.car.VehicleCatalogImpl
import com.hopcape.odo.core.data.car.seedVehicleReferenceData
import com.hopcape.odo.core.data.db.DriverFactory
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.db.createOdoDatabase
import com.hopcape.odo.core.data.owner.OwnerProfileRepositoryImpl
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import app.cash.sqldelight.db.SqlDriver
import org.koin.dsl.module

/**
 * DI graph for the data layer. The platform [DriverFactory] is provided by the
 * `:app` bootstrap (Android needs a Context); this module wires the DB and the
 * domain ports on top of it.
 */
val coreDataModule = module {
    // The driver is its own definition rather than an anonymous argument to the database
    // so there is exactly one connection per process and something can reach it: an
    // end-to-end test needs to reset the tables between runs, and the local DB has no
    // delete API of its own (nothing in the product deletes a car yet).
    single<SqlDriver> { get<DriverFactory>().create() }
    single<OdoDatabase> { createOdoDatabase(get()).also(::seedVehicleReferenceData) }
    single<CarRepository> { CarRepositoryImpl(database = get()) }
    single<OwnerProfileRepository> { OwnerProfileRepositoryImpl(database = get()) }
    single<VehicleCatalog> { VehicleCatalogImpl(database = get()) }
    // Development stub: it knows a couple of hardcoded plates so the "is this your
    // car?" path can be walked, and answers RegistrationNotFound for everything else.
    // MUST be swapped for a real adapter before launch — this one line is the swap.
    single<VehicleRegistryLookup> { StubVehicleRegistryLookup() }
}
