package com.hopcape.odo.core.data

import com.hopcape.odo.core.data.car.CarRepositoryImpl
import com.hopcape.odo.core.data.car.PrimaryCarProvider
import com.hopcape.odo.core.data.car.StubVehicleRegistryLookup
import com.hopcape.odo.core.data.car.VehicleCatalogImpl
import com.hopcape.odo.core.data.car.seedVehicleReferenceData
import com.hopcape.odo.core.data.cost.LocalFuelPriceProvider
import com.hopcape.odo.core.data.cost.seedFuelPrices
import com.hopcape.odo.core.data.db.DriverFactory
import com.hopcape.odo.core.data.document.DocumentRemoteDataSource
import com.hopcape.odo.core.data.document.DocumentRepositoryImpl
import com.hopcape.odo.core.data.document.FakeDocumentRemoteDataSource
import com.hopcape.odo.core.data.document.FreeTierDocumentAllowance
import com.hopcape.odo.core.data.fairness.FairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.FairnessRepositoryImpl
import com.hopcape.odo.core.data.fairness.FakeFairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.FakeOverchargeRemoteDataSource
import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.core.data.entitlement.AlwaysProEntitlement
import com.hopcape.odo.core.data.fairness.OverchargeReportRepositoryImpl
import com.hopcape.odo.core.data.fairness.RepositoryFairnessAnalyzer
import com.hopcape.odo.core.data.health.HealthScoreRepositoryImpl
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.owner.ProfileCityProvider
import com.hopcape.odo.core.data.servicelog.FakeServiceLogRemoteDataSource
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.servicelog.ServiceLogRepositoryImpl
import com.hopcape.odo.core.data.sync.NoopSyncScheduler
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.db.createOdoDatabase
import com.hopcape.odo.core.data.owner.OwnerProfileRepositoryImpl
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceOverrides
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.entitlement.ProEntitlement
import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.fairness.repository.OverchargeReportRepository
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
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
    // end-to-end test needs to reset the tables between runs, and removing a car is a
    // soft delete, which leaves the tables populated.
    single<SqlDriver> { get<DriverFactory>().create() }
    single<OdoDatabase> {
        createOdoDatabase(get())
            .also(::seedVehicleReferenceData)
            .also(::seedFuelPrices)
    }
    single<CarRepository> {
        CarRepositoryImpl(database = get(), telemetry = get(), scheduler = get())
    }
    // Which car every per-car screen is about. A `single` holding a hot StateFlow, so a
    // navigation handler can name the car synchronously the moment it is tapped.
    single<ActiveCarProvider> { PrimaryCarProvider(cars = get(), telemetry = get()) }
    single<OwnerProfileRepository> { OwnerProfileRepositoryImpl(database = get()) }
    single<VehicleCatalog> { VehicleCatalogImpl(database = get()) }

    // Observability for the whole data layer, behind one facade. A `single`: it holds no
    // per-call state — the trace comes from the calling coroutine, not from this object.
    single { DataTelemetry(logger = get(), tracer = get(), crash = get()) }

    // The platform scheduler is a no-op until M5. THIS ONE LINE is the swap: the
    // repositories already ask for a sync after every write, so a real WorkManager-backed
    // scheduler starts draining the outbox without a single call site changing.
    single<SyncScheduler> { NoopSyncScheduler() }

    single<ServiceLogRepository> {
        ServiceLogRepositoryImpl(database = get(), telemetry = get(), scheduler = get(), remote = get())
    }
    single<DocumentRepository> {
        DocumentRepositoryImpl(database = get(), telemetry = get(), scheduler = get(), remote = get())
    }
    // Score history, not today's score: the number on screen is computed on read, and
    // this only keeps what the month delta is measured against.
    single<HealthScoreRepository> {
        HealthScoreRepositoryImpl(database = get(), telemetry = get(), scheduler = get())
    }
    single<FairnessRepository> { FairnessRepositoryImpl(remote = get(), telemetry = get()) }
    // The one way to get a verdict. Any feature injects the port and gets the same
    // benchmarks, so no screen carries a benchmark table of its own.
    single<FairnessAnalyzer> { RepositoryFairnessAnalyzer(fairness = get()) }
    single<OverchargeReportRepository> {
        OverchargeReportRepositoryImpl(
            database = get(),
            telemetry = get(),
            idGenerator = get(),
            scheduler = get(),
            remote = get(),
        )
    }
    // The owner's city, read from their profile — null until they set it, which is what
    // keeps fairness silent rather than guessing.
    single<CurrentCityProvider> { ProfileCityProvider(database = get(), telemetry = get()) }

    // Remote data sources. These three lines are the entire swap when :core:network lands:
    // every repository above already talks to the port, not to a client.
    single<ServiceLogRemoteDataSource> { FakeServiceLogRemoteDataSource() }
    single<DocumentRemoteDataSource> { FakeDocumentRemoteDataSource() }
    single<FairnessRemoteDataSource> { FakeFairnessRemoteDataSource() }
    single<OverchargeRemoteDataSource> { FakeOverchargeRemoteDataSource() }
    // Development stub: it knows a couple of hardcoded plates so the "is this your
    // car?" path can be walked, and answers RegistrationNotFound for everything else.
    // MUST be swapped for a real adapter before launch — this one line is the swap.
    single<VehicleRegistryLookup> { StubVehicleRegistryLookup() }

    // Everyone is on the free tier until something sells a subscription, so this answers
    // truthfully rather than standing in for a reader that does not exist. The vault asks
    // before every add; a real entitlement adapter swaps in on this one line.
    single<DocumentAllowance> { FreeTierDocumentAllowance() }

    // Everyone is Pro until Razorpay lands in M6. Answering false would hide Pro-gated
    // content behind a paywall that cannot take money yet. MUST be swapped before launch —
    // this one line is the swap.
    single<ProEntitlement> { AlwaysProEntitlement() }

    // Fuel prices live in a local table so correcting one never needs a release: the seed
    // fills it on first launch, M4's fuel-prices feed writes fresher rows on top, and the
    // owner's own rate outranks both. One object serves the read and the override ports.
    single { LocalFuelPriceProvider(database = get(), telemetry = get()) }
    single<FuelPriceProvider> { get<LocalFuelPriceProvider>() }
    single<FuelPriceOverrides> { get<LocalFuelPriceProvider>() }
}
