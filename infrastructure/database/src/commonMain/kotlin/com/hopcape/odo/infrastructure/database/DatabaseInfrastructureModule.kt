package com.hopcape.odo.infrastructure.database

import app.cash.sqldelight.db.SqlDriver
import com.hopcape.analytics.api.AnalyticsEventStore
import com.hopcape.odo.core.data.car.CarLocalDataSource
import com.hopcape.odo.core.data.cost.FuelFillLocalDataSource
import com.hopcape.odo.core.data.document.DocumentLocalDataSource
import com.hopcape.odo.core.data.fairness.OverchargeReportLocalDataSource
import com.hopcape.odo.core.data.health.HealthScoreLocalDataSource
import com.hopcape.odo.core.data.owner.ProfileLocalDataSource
import com.hopcape.odo.core.data.reminder.ReminderLocalDataSource
import com.hopcape.odo.core.data.servicelog.ServiceLogLocalDataSource
import com.hopcape.odo.core.data.scan.ScanUsageLocalDataSource
import com.hopcape.odo.core.data.settings.AppSettingsLocalDataSource
import com.hopcape.odo.core.data.sync.OwnershipAdoption
import com.hopcape.odo.core.data.trip.TripLocalDataSource
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceOverrides
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.LocalUserDataWipe
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.sync.SyncStatusProvider
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncRunObserver
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.analytics.SqlDelightAnalyticsEventStore
import com.hopcape.odo.infrastructure.database.car.CarSyncTable
import com.hopcape.odo.infrastructure.database.car.CarSyncable
import com.hopcape.odo.infrastructure.database.cost.FuelFillSyncTable
import com.hopcape.odo.infrastructure.database.cost.FuelFillSyncable
import com.hopcape.odo.infrastructure.database.car.SqlDelightCarLocalDataSource
import com.hopcape.odo.infrastructure.database.car.VehicleCatalogImpl
import com.hopcape.odo.infrastructure.database.car.seedVehicleReferenceData
import com.hopcape.odo.infrastructure.database.cost.LocalFuelPriceProvider
import com.hopcape.odo.core.domain.refuel.PendingFillStore
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.infrastructure.database.cost.SqlDelightFuelFillLocalDataSource
import com.hopcape.odo.infrastructure.database.refuel.SqlDelightPendingFillStore
import com.hopcape.odo.infrastructure.database.refuel.SqlDelightRefuelDetectionStore
import com.hopcape.odo.infrastructure.database.cost.seedFuelPrices
import com.hopcape.odo.infrastructure.database.db.DriverFactory
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.createOdoDatabase
import com.hopcape.odo.infrastructure.database.document.DocumentSyncTable
import com.hopcape.odo.infrastructure.database.document.DocumentSyncable
import com.hopcape.odo.infrastructure.database.document.SqlDelightDocumentLocalDataSource
import com.hopcape.odo.infrastructure.database.fairness.OverchargeReportSyncTable
import com.hopcape.odo.infrastructure.database.fairness.OverchargeReportSyncable
import com.hopcape.odo.infrastructure.database.fairness.SqlDelightOverchargeReportLocalDataSource
import com.hopcape.odo.infrastructure.database.health.HealthScoreSyncTable
import com.hopcape.odo.infrastructure.database.health.HealthScoreSyncable
import com.hopcape.odo.infrastructure.database.health.SqlDelightHealthScoreLocalDataSource
import com.hopcape.odo.infrastructure.database.owner.ProfileSyncTable
import com.hopcape.odo.infrastructure.database.owner.ProfileSyncable
import com.hopcape.odo.infrastructure.database.owner.SqlDelightProfileLocalDataSource
import com.hopcape.odo.infrastructure.database.reminder.ReminderSyncTable
import com.hopcape.odo.infrastructure.database.reminder.ReminderSyncable
import com.hopcape.odo.infrastructure.database.reminder.SqlDelightReminderLocalDataSource
import com.hopcape.odo.infrastructure.database.servicelog.ServiceLogSyncTable
import com.hopcape.odo.infrastructure.database.servicelog.ServiceLogSyncable
import com.hopcape.odo.infrastructure.database.servicelog.SqlDelightServiceLogLocalDataSource
import com.hopcape.odo.infrastructure.database.scan.SqlDelightScanUsageLocalDataSource
import com.hopcape.odo.infrastructure.database.record.SqlDelightExportCreditsLocalDataSource
import com.hopcape.odo.infrastructure.database.record.SqlDelightRecordExportUsageLocalDataSource
import com.hopcape.odo.core.data.record.ExportCreditsLocalDataSource
import com.hopcape.odo.core.data.record.RecordExportUsageLocalDataSource
import com.hopcape.odo.infrastructure.database.settings.SqlDelightAppSettingsLocalDataSource
import com.hopcape.odo.infrastructure.database.sync.SqlDelightLocalUserDataWipe
import com.hopcape.odo.infrastructure.database.sync.SqlDelightOwnershipAdoption
import com.hopcape.odo.infrastructure.database.sync.SqlDelightSyncStatusProvider
import com.hopcape.odo.infrastructure.database.sync.SqlDelightSynchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner
import com.hopcape.odo.infrastructure.database.trip.SqlDelightTripLocalDataSource
import com.hopcape.odo.infrastructure.database.trip.TripSessionStoreImpl
import com.hopcape.odo.infrastructure.database.trip.TripSyncTable
import com.hopcape.odo.infrastructure.database.trip.TripSyncable
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The SQLDelight database and the local-data-source adapters that implement the ports
 * `:core:data`'s repositories depend on.
 *
 * Listed **before** `coreSyncModule` in `initKoin` — the engine collects every registered
 * `Syncable` with `getAll()`, so the module that registers them has to run first. Also
 * before `coreDataModule` isn't required (Koin resolves `get()` lazily regardless of list
 * order), but the two are natural neighbours: this module supplies the `LocalDataSource`
 * ports `coreDataModule`'s repositories ask for.
 *
 * Only this `val` is public — everything this module wires stays `internal`, same as every
 * other infrastructure module.
 */
val databaseInfrastructureModule = module {
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

    single<CarLocalDataSource> { SqlDelightCarLocalDataSource(database = get()) }
    single {
        CarSyncable(
            runner = SyncRunner(
                entity = SyncEntity.CARS,
                table = CarSyncTable(
                    database = get(),
                    remote = get(),
                    telemetry = get(),
                    ownerId = { get<CurrentOwnerProvider>().currentOwnerId().value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    // Shared by OwnerProfileRepositoryImpl and ProfileCityProvider — both read the same
    // `profiles` table, so there is one local data source rather than two paths to the
    // same row.
    single<ProfileLocalDataSource> { SqlDelightProfileLocalDataSource(database = get()) }
    single {
        ProfileSyncable(
            runner = SyncRunner(
                entity = SyncEntity.PROFILES,
                table = ProfileSyncTable(
                    database = get(),
                    remote = get(),
                    ownerId = { get<CurrentOwnerProvider>().currentOwnerId().value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    // Device settings — theme, units, notification topics. No Syncable adapter:
    // `app_settings` mirrors no server table, so there is nothing to push.
    single<AppSettingsLocalDataSource> { SqlDelightAppSettingsLocalDataSource(database = get()) }

    // The monthly scan tally. No Syncable adapter for the same reason as app_settings:
    // `scan_usage` mirrors no server table, because extraction never leaves the device.
    single<ScanUsageLocalDataSource> { SqlDelightScanUsageLocalDataSource(database = get()) }
    single<RecordExportUsageLocalDataSource> { SqlDelightRecordExportUsageLocalDataSource(database = get()) }
    single<ExportCreditsLocalDataSource> { SqlDelightExportCreditsLocalDataSource(database = get()) }

    // The durable analytics event queue behind :observability:analytics's AnalyticsConfig
    // .eventStore. Same reason as app_settings: no Syncable adapter, because there is no
    // server table this mirrors — Firebase/PostHog are the systems of record for delivered
    // events.
    single<AnalyticsEventStore> { SqlDelightAnalyticsEventStore(database = get(), telemetry = get()) }

    single<ServiceLogLocalDataSource> { SqlDelightServiceLogLocalDataSource(database = get()) }
    single {
        ServiceLogSyncable(
            runner = SyncRunner(
                entity = SyncEntity.SERVICE_LOGS,
                table = ServiceLogSyncTable(
                    database = get(),
                    remote = get(),
                    blobs = get(),
                    // Read at push time, not at construction: the active car changes while
                    // the app runs, and a sync started before onboarding finished has
                    // nothing to pull.
                    carId = { get<ActiveCarProvider>().activeCarId.value?.value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    single<DocumentLocalDataSource> { SqlDelightDocumentLocalDataSource(database = get()) }
    single {
        DocumentSyncable(
            runner = SyncRunner(
                entity = SyncEntity.DOCUMENTS,
                table = DocumentSyncTable(
                    database = get(),
                    remote = get(),
                    blobs = get(),
                    carId = { get<ActiveCarProvider>().activeCarId.value?.value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    // Score history, not today's score: the number on screen is computed on read, and
    // this only keeps what the month delta is measured against.
    single<HealthScoreLocalDataSource> { SqlDelightHealthScoreLocalDataSource(database = get()) }
    single {
        HealthScoreSyncable(
            runner = SyncRunner(
                entity = SyncEntity.HEALTH_SCORES,
                table = HealthScoreSyncTable(
                    database = get(),
                    remote = get(),
                    carId = { get<ActiveCarProvider>().activeCarId.value?.value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    single<OverchargeReportLocalDataSource> { SqlDelightOverchargeReportLocalDataSource(database = get()) }
    single {
        OverchargeReportSyncable(
            runner = SyncRunner(
                entity = SyncEntity.OVERCHARGE_REPORTS,
                table = OverchargeReportSyncTable(database = get(), remote = get()),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    // Custom reminders + dismissals. The derived reminders have no rows — the feed
    // recomputes them — so this table only carries what cannot be recomputed.
    single<ReminderLocalDataSource> { SqlDelightReminderLocalDataSource(database = get(), telemetry = get()) }
    single {
        ReminderSyncable(
            runner = SyncRunner(
                entity = SyncEntity.REMINDERS,
                table = ReminderSyncTable(
                    database = get(),
                    remote = get(),
                    carId = { get<ActiveCarProvider>().activeCarId.value?.value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    // Tanks of fuel the owner confirmed. Detections still waiting for an answer are not
    // here — they sit in `pending_fills`, which is device-local and has no sync columns at
    // all, so Odo's guess about a payment can never reach a server.
    single<FuelFillLocalDataSource> { SqlDelightFuelFillLocalDataSource(database = get()) }
    single {
        FuelFillSyncable(
            runner = SyncRunner(
                entity = SyncEntity.FUEL_FILLS,
                table = FuelFillSyncTable(
                    database = get(),
                    remote = get(),
                    carId = { get<ActiveCarProvider>().activeCarId.value?.value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class

    // Everything the payment-notification listener is allowed to do, and what it has learned.
    // Device-local by design: the permission belongs to one phone, and so do its mistakes.
    single<RefuelDetectionStore> { SqlDelightRefuelDetectionStore(database = get(), clock = get()) }

    // Detections waiting for an answer. Separate from the store above because it holds the
    // feature's output rather than its configuration, and a different screen reads it.
    single<PendingFillStore> { SqlDelightPendingFillStore(database = get()) }

    single<VehicleCatalog> { VehicleCatalogImpl(database = get()) }

    // Fuel prices live in a local table so correcting one never needs a release: the seed
    // fills it on first launch, M4's fuel-prices feed writes fresher rows on top, and the
    // owner's own rate outranks both. One object serves the read and the override ports.
    single { LocalFuelPriceProvider(database = get(), telemetry = get()) }
    single<FuelPriceProvider> { get<LocalFuelPriceProvider>() }
    single<FuelPriceOverrides> { get<LocalFuelPriceProvider>() }

    // The engine's bookkeeping. Lives here, not in :core:sync, because it is the only half
    // that needs SQLDelight — the cursor has to move in the same transaction as the rows it
    // describes (SYNC_DESIGN §4.2).
    single<Synchronizer> { SqlDelightSynchronizer(database = get(), telemetry = get()) }

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

    // Automatically-detected drives.
    single<TripLocalDataSource> { SqlDelightTripLocalDataSource(database = get()) }
    single {
        TripSyncable(
            runner = SyncRunner(
                entity = SyncEntity.TRIPS,
                table = TripSyncTable(
                    database = get(),
                    remote = get(),
                    carId = { get<ActiveCarProvider>().activeCarId.value?.value },
                ),
                database = get(),
                telemetry = get(),
            ),
        )
    } bind Syncable::class
    // The crash-resume journal :core:triptracker's engine reads/writes through its own
    // TripSessionStore port.
    single<TripSessionStore> { TripSessionStoreImpl(database = get()) }

    // Moves rows created before sign-in onto the account that signed in (SYNC_DESIGN §9).
    single<OwnershipAdoption> {
        SqlDelightOwnershipAdoption(
            database = get(),
            telemetry = get(),
            // The constant, never the provider: once that answers with the signed-in
            // user's id, reading it here would make adoption a permanent no-op.
            placeholderOwnerId = OwnerId.LOCAL_PLACEHOLDER.value,
        )
    }
}
