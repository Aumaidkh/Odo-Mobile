package com.hopcape.odo.core.data

import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.core.data.car.CarRepositoryImpl
import com.hopcape.odo.core.data.car.FakeCarRemoteDataSource
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
import com.hopcape.odo.core.data.health.FakeHealthScoreRemoteDataSource
import com.hopcape.odo.core.data.health.HealthScoreRemoteDataSource
import com.hopcape.odo.core.data.health.HealthScoreRepositoryImpl
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.owner.FakeProfileRemoteDataSource
import com.hopcape.odo.core.data.owner.ProfileCityProvider
import com.hopcape.odo.core.data.owner.ProfileRemoteDataSource
import com.hopcape.odo.core.data.remote.FakeRemoteFileStorage
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.data.servicelog.FakeServiceLogRemoteDataSource
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.servicelog.ServiceLogRepositoryImpl
import com.hopcape.odo.core.data.sync.NoopSyncScheduler
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.core.data.sync.OwnershipAdoption
import com.hopcape.odo.core.data.sync.SessionSyncGate
import com.hopcape.odo.core.data.sync.SqlDelightLocalUserDataWipe
import com.hopcape.odo.core.data.sync.SqlDelightOwnershipAdoption
import com.hopcape.odo.core.data.sync.SqlDelightSyncStatusProvider
import com.hopcape.odo.core.data.sync.SqlDelightSynchronizer
import com.hopcape.odo.core.sync.SyncGate
import com.hopcape.odo.core.sync.SyncRunObserver
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.db.createOdoDatabase
import com.hopcape.odo.core.data.owner.OwnerProfileRepositoryImpl
import com.hopcape.odo.core.data.settings.AppSettingsRepositoryImpl
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
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.LocalUserDataWipe
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.sync.SyncStatusProvider
import app.cash.sqldelight.db.SqlDriver
import org.koin.dsl.bind
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
    // Each repository is bound twice: once as the port features use, once as the Syncable
    // the engine collects. `bind`, not a second `single<Syncable>` — six definitions of the
    // same type would override one another and getAll() would return one entity.
    single {
        CarRepositoryImpl(
            database = get(),
            telemetry = get(),
            scheduler = get(),
            remote = get(),
            syncTelemetry = get(),
            ownerId = { get<CurrentOwnerProvider>().currentOwnerId().value },
        )
    } bind Syncable::class
    single<CarRepository> { get<CarRepositoryImpl>() }
    // Which car every per-car screen is about. A `single` holding a hot StateFlow, so a
    // navigation handler can name the car synchronously the moment it is tapped.
    single<ActiveCarProvider> { PrimaryCarProvider(cars = get(), telemetry = get()) }
    single {
        OwnerProfileRepositoryImpl(
            database = get(),
            telemetry = get(),
            scheduler = get(),
            remote = get(),
            syncTelemetry = get(),
            ownerId = { get<CurrentOwnerProvider>().currentOwnerId().value },
        )
    } bind Syncable::class
    single<OwnerProfileRepository> { get<OwnerProfileRepositoryImpl>() }
    // Device settings — theme, units, notification topics. Deliberately no scheduler:
    // `app_settings` mirrors no server table, so there is nothing to push.
    single<AppSettingsRepository> { AppSettingsRepositoryImpl(database = get(), telemetry = get()) }
    single<VehicleCatalog> { VehicleCatalogImpl(database = get()) }

    // Observability for the whole data layer, behind one facade. A `single`: it holds no
    // per-call state — the trace comes from the calling coroutine, not from this object.
    single { DataTelemetry(logger = get(), tracer = get(), crash = get()) }

    // The platform scheduler is a no-op until the platform module binds a real one. THIS
    // ONE LINE is the swap: the repositories already ask for a sync after every write.
    single<SyncScheduler> { NoopSyncScheduler() }

    // The engine's bookkeeping. Lives here, not in :core:sync, because it is the only half
    // that needs SQLDelight — the cursor has to move in the same transaction as the rows it
    // describes (SYNC_DESIGN §4.2).
    single<Synchronizer> { SqlDelightSynchronizer(database = get(), telemetry = get()) }

    // Whether a run may happen at all, and whether this install's rows belong to the
    // account yet. Asking for a token rather than a boolean refreshes a stale one on the
    // way past, so a run never starts with one that dies mid-push.
    single<SyncGate> { SessionSyncGate(tokens = get(), owners = get(), adoption = get()) }

    // Moves rows created before sign-in onto the account that signed in (SYNC_DESIGN §9).
    // The placeholder id comes from the provider that stamps it, so the two can never drift.
    // Reads a stored file and puts it in a bucket. Only the two entities that name files
    // take one.
    single { BlobUploader(files = get(), storage = get(), telemetry = get()) }

    // What the UI may know about sync. Counts are read from the outbox rather than kept in
    // memory, so they survive a process death and cannot drift from the rows themselves.
    single { SqlDelightSyncStatusProvider(database = get(), telemetry = get()) }
    single<SyncStatusProvider> { get<SqlDelightSyncStatusProvider>() }
    single<SyncRunObserver> { get<SqlDelightSyncStatusProvider>() }

    // Sign-out forgets the owner's local copy. Hard deletes, and it clears the sync
    // cursors — a stale one would make the next sign-in skip everything written in between.
    single<LocalUserDataWipe> {
        SqlDelightLocalUserDataWipe(database = get(), files = get(), telemetry = get())
    }

    single<OwnershipAdoption> {
        SqlDelightOwnershipAdoption(
            database = get(),
            telemetry = get(),
            // The constant, never the provider: once that answers with the signed-in
            // user's id, reading it here would make adoption a permanent no-op.
            placeholderOwnerId = OwnerId.LOCAL_PLACEHOLDER.value,
        )
    }

    // A `single` bound to two types: the repository the features use, and the Syncable the
    // engine collects with getAll(). One instance either way — the sync half needs the same
    // row↔DTO mapping the read half has.
    single {
        ServiceLogRepositoryImpl(
            database = get(),
            telemetry = get(),
            scheduler = get(),
            remote = get(),
            syncTelemetry = get(),
            blobs = get(),
            // Read at push time, not at construction: the active car changes while the app
            // runs, and a sync started before onboarding finished has nothing to pull.
            activeCarId = { get<ActiveCarProvider>().activeCarId.value?.value },
        )
        // `bind`, not a second `single<Syncable>`: six definitions of the same type would
        // override one another and getAll() would come back with one entity.
    } bind Syncable::class
    single<ServiceLogRepository> { get<ServiceLogRepositoryImpl>() }
    single {
        DocumentRepositoryImpl(
            database = get(),
            telemetry = get(),
            scheduler = get(),
            remote = get(),
            syncTelemetry = get(),
            blobs = get(),
            carId = { get<ActiveCarProvider>().activeCarId.value?.value },
        )
    } bind Syncable::class
    single<DocumentRepository> { get<DocumentRepositoryImpl>() }
    // Score history, not today's score: the number on screen is computed on read, and
    // this only keeps what the month delta is measured against.
    single {
        HealthScoreRepositoryImpl(
            database = get(),
            telemetry = get(),
            scheduler = get(),
            remote = get(),
            syncTelemetry = get(),
            carId = { get<ActiveCarProvider>().activeCarId.value?.value },
        )
    } bind Syncable::class
    single<HealthScoreRepository> { get<HealthScoreRepositoryImpl>() }
    single<FairnessRepository> { FairnessRepositoryImpl(remote = get(), telemetry = get()) }
    // The one way to get a verdict. Any feature injects the port and gets the same
    // benchmarks, so no screen carries a benchmark table of its own.
    single<FairnessAnalyzer> { RepositoryFairnessAnalyzer(fairness = get()) }
    single {
        OverchargeReportRepositoryImpl(
            database = get(),
            telemetry = get(),
            idGenerator = get(),
            scheduler = get(),
            remote = get(),
            syncTelemetry = get(),
        )
    } bind Syncable::class
    single<OverchargeReportRepository> { get<OverchargeReportRepositoryImpl>() }
    // The owner's city, read from their profile — null until they set it, which is what
    // keeps fairness silent rather than guessing.
    single<CurrentCityProvider> { ProfileCityProvider(database = get(), telemetry = get()) }

    // Remote data sources — the offline-safe defaults. `supabaseModule` is listed after this
    // module in `initKoin`, and replaces every one of these with a real adapter as soon as
    // the build carries Supabase credentials. Koin allows later definitions to override
    // earlier ones, so with no credentials the fakes simply stand.
    single<ServiceLogRemoteDataSource> { FakeServiceLogRemoteDataSource() }
    single<DocumentRemoteDataSource> { FakeDocumentRemoteDataSource() }
    single<FairnessRemoteDataSource> { FakeFairnessRemoteDataSource() }
    single<OverchargeRemoteDataSource> { FakeOverchargeRemoteDataSource() }
    single<RemoteFileStorage> { FakeRemoteFileStorage() }
    single<CarRemoteDataSource> { FakeCarRemoteDataSource() }
    single<ProfileRemoteDataSource> { FakeProfileRemoteDataSource() }
    single<HealthScoreRemoteDataSource> { FakeHealthScoreRemoteDataSource() }
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
