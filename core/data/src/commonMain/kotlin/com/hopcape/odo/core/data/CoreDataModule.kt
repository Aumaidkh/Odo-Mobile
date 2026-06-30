package com.hopcape.odo.core.data

import com.hopcape.odo.core.data.car.CarRepositoryImpl
import com.hopcape.odo.core.data.car.VehicleCatalogImpl
import com.hopcape.odo.core.data.car.seedVehicleReferenceData
import com.hopcape.odo.core.data.db.DriverFactory
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.db.createOdoDatabase
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.repository.CarRepository
import org.koin.dsl.module

/**
 * DI graph for the data layer. The platform [DriverFactory] is provided by the
 * `:app` bootstrap (Android needs a Context); this module wires the DB and the
 * domain ports on top of it.
 */
val coreDataModule = module {
    single<OdoDatabase> {
        createOdoDatabase(get<DriverFactory>().create()).also(::seedVehicleReferenceData)
    }
    single<CarRepository> { CarRepositoryImpl(database = get()) }
    single<VehicleCatalog> { VehicleCatalogImpl(database = get()) }
}
