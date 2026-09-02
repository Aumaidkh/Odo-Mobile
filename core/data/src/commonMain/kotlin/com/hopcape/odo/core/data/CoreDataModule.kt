package com.hopcape.odo.core.data

import com.hopcape.odo.core.data.appstatus.AlwaysAvailableAppStatusSource
import com.hopcape.odo.core.data.support.BuiltInSupportContacts
import com.hopcape.odo.core.domain.support.SupportContacts
import com.hopcape.odo.core.data.appstatus.DefaultAppStatusProvider
import com.hopcape.odo.core.data.appstatus.MaintenanceAwareSyncGate
import com.hopcape.odo.core.data.appstatus.observability.AppStatusTelemetry
import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.core.data.car.CarRepositoryImpl
import com.hopcape.odo.core.data.auth.OfflineAccountEraser
import com.hopcape.odo.core.data.car.FakeCarRemoteDataSource
import com.hopcape.odo.core.data.car.FakeVehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.car.PrimaryCarProvider
import com.hopcape.odo.core.data.car.StubVehicleRegistryLookup
import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.city.CityRemoteDataSource
import com.hopcape.odo.core.data.city.CitySubmissionRemoteDataSource
import com.hopcape.odo.core.data.city.FakeCityRemoteDataSource
import com.hopcape.odo.core.data.entitlement.EntitlementOverrideRemoteDataSource
import com.hopcape.odo.core.domain.entitlement.OverridableEntitlementSource
import com.hopcape.odo.core.data.entitlement.FakeEntitlementOverrideRemoteDataSource
import com.hopcape.odo.core.data.city.FakeCitySubmissionRemoteDataSource
import com.hopcape.odo.core.data.cost.FuelFillRepositoryImpl
import com.hopcape.odo.core.data.scan.LocalScanUsage
import com.hopcape.odo.core.data.scan.UnconfiguredBillExtractor
import com.hopcape.odo.core.data.scan.UnconfiguredDocumentExtractor
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.DocumentExtractor
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import com.hopcape.odo.core.data.cost.FakeFuelFillRemoteDataSource
import com.hopcape.odo.core.data.owner.FakeQuestionAnswerRemoteDataSource
import com.hopcape.odo.core.data.owner.QuestionAnswerRemoteDataSource
import com.hopcape.odo.core.data.owner.QuestionnaireRepositoryImpl
import com.hopcape.odo.core.data.cost.FuelFillRemoteDataSource
import com.hopcape.odo.core.data.document.DocumentRemoteDataSource
import com.hopcape.odo.core.data.document.DocumentRepositoryImpl
import com.hopcape.odo.core.data.document.FakeDocumentRemoteDataSource
import com.hopcape.odo.core.data.fairness.FairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.FairnessRepositoryImpl
import com.hopcape.odo.core.data.fairness.FakeFairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.FakeOverchargeRemoteDataSource
import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.core.data.entitlement.EntitlementDocumentAllowance
import com.hopcape.odo.core.data.entitlement.EntitlementScanAllowance
import com.hopcape.odo.core.data.record.LocalExportCredits
import com.hopcape.odo.core.data.record.LocalRecordExportUsage
import com.hopcape.odo.core.domain.record.entitlement.ExportCredits
import com.hopcape.odo.core.domain.record.entitlement.RecordExportUsage
import com.hopcape.odo.core.data.entitlement.FreePlanEntitlementSource
import com.hopcape.odo.core.data.subscription.NoopSubscriptionIdentity
import com.hopcape.odo.core.data.fairness.OverchargeReportRepositoryImpl
import com.hopcape.odo.core.data.fairness.RepositoryFairnessAnalyzer
import com.hopcape.odo.core.data.health.FakeHealthScoreRemoteDataSource
import com.hopcape.odo.core.data.health.HealthScoreRemoteDataSource
import com.hopcape.odo.core.data.health.HealthScoreRepositoryImpl
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.odometer.CurrentOdometerProviderImpl
import com.hopcape.odo.core.data.owner.FakeProfileRemoteDataSource
import com.hopcape.odo.core.data.owner.ProfileCityProvider
import com.hopcape.odo.core.data.owner.ProfileRemoteDataSource
import com.hopcape.odo.core.data.remote.FakeRemoteFileStorage
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.data.reminder.FakeReminderRemoteDataSource
import com.hopcape.odo.core.data.reminder.ReminderRemoteDataSource
import com.hopcape.odo.core.data.reminder.ReminderRepositoryImpl
import com.hopcape.odo.core.data.servicelog.FakeServiceLogRemoteDataSource
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.servicelog.ServiceLogRepositoryImpl
import com.hopcape.odo.core.data.notification.NoopCustomReminderScheduler
import com.hopcape.odo.core.data.notification.NoopDocumentReminderScheduler
import com.hopcape.odo.core.data.sync.NoopSyncScheduler
import com.hopcape.odo.core.data.sync.BlobDownloader
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.core.data.sync.SessionSyncGate
import com.hopcape.odo.core.data.trip.FakeTripRemoteDataSource
import com.hopcape.odo.core.data.trip.TripRemoteDataSource
import com.hopcape.odo.core.data.trip.TripRepositoryImpl
import com.hopcape.odo.core.sync.SyncGate
import com.hopcape.odo.core.platform.notification.CustomReminderScheduler
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.data.owner.OwnerProfileRepositoryImpl
import com.hopcape.odo.core.data.settings.AppSettingsRepositoryImpl
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.auth.AccountEraser
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.subscription.SubscriptionIdentity
import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.fairness.repository.OverchargeReportRepository
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.data.showcase.observability.ShowcaseTelemetryImpl
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseTelemetry
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.domain.appstatus.AppStatusSource
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Names [SessionSyncGate] so [MaintenanceAwareSyncGate] can wrap it without resolving itself. */
private const val QUALIFIER_SESSION_SYNC_GATE = "session"

/**
 * DI graph for the data layer's ports and repositories.
 *
 * The SQLDelight database and every `LocalDataSource` implementation live in
 * `:infrastructure:database`'s `databaseInfrastructureModule` instead — this module never
 * imports SQLDelight. It is listed **after** `databaseInfrastructureModule` in `initKoin`,
 * so the `LocalDataSource` ports the repositories below ask for via `get()` already resolve
 * to a real implementation.
 */
val coreDataModule = module {
    single { CarRepositoryImpl(local = get(), telemetry = get(), scheduler = get()) }
    single<CarRepository> { get<CarRepositoryImpl>() }
    // Which car every per-car screen is about. A `single` holding a hot StateFlow, so a
    // navigation handler can name the car synchronously the moment it is tapped.
    single<ActiveCarProvider> { PrimaryCarProvider(cars = get(), telemetry = get()) }
    single { OwnerProfileRepositoryImpl(local = get(), telemetry = get(), scheduler = get()) }
    single<OwnerProfileRepository> { get<OwnerProfileRepositoryImpl>() }
    // Device settings — theme, units, notification topics. Deliberately no scheduler:
    // `app_settings` mirrors no server table, so there is nothing to push.
    single<AppSettingsRepository> { AppSettingsRepositoryImpl(local = get(), telemetry = get()) }
    // The coach marks' one-at-a-time rule (#227). A `single` because "exactly one grant"
    // is a statement about the app; the store behind it is a platform binding. The
    // telemetry is what makes the whole showcase's premise checkable (#235).
    single<ShowcaseTelemetry> { ShowcaseTelemetryImpl(logger = get(), analytics = get()) }
    single { ShowcaseArbiter(store = get(), telemetry = get()) }
    // Automatically-detected drives. TripSyncable (databaseInfrastructureModule) drains the
    // outbox now, but this repository still has no scheduler — a write only requests a sync
    // once something needs the result sooner than the next scheduled run, and nothing does
    // yet.
    single<TripRepository> { TripRepositoryImpl(local = get(), telemetry = get()) }
    // The trip-aware "current odometer" — the last manual reading plus every counted trip
    // since it. A `single` for the same reason as `ActiveCarProvider`: combines two
    // repositories already registered above, nothing new to fail.
    single<CurrentOdometerProvider> { CurrentOdometerProviderImpl(serviceLogs = get(), trips = get()) }

    // Observability for the whole data layer, behind one facade. A `single`: it holds no
    // per-call state — the trace comes from the calling coroutine, not from this object.
    single { DataTelemetry(logger = get(), tracer = get(), crash = get()) }

    // The platform scheduler is a no-op until the platform module binds a real one. THIS
    // ONE LINE is the swap: the repositories already ask for a sync after every write.
    single<SyncScheduler> { NoopSyncScheduler() }

    // Same shape for document reminders: the writers already ask for a refresh after every
    // change, and the platform module binds the scheduler that delivers them.
    single<DocumentReminderScheduler> { NoopDocumentReminderScheduler() }
    single<CustomReminderScheduler> { NoopCustomReminderScheduler() }

    // Whether a run may happen at all, and whether this install's rows belong to the
    // account yet. Asking for a token rather than a boolean refreshes a stale one on the
    // way past, so a run never starts with one that dies mid-push.
    //
    // Qualified rather than the plain SyncGate: MaintenanceAwareSyncGate below wraps this
    // one, and a second unqualified single<SyncGate> would resolve *itself* instead —
    // this qualifier is what lets it name the thing it decorates.
    single<SyncGate>(named(QUALIFIER_SESSION_SYNC_GATE)) {
        SessionSyncGate(tokens = get(), sessions = get(), owners = get(), adoption = get())
    }
    // The engine's actual SyncGate: closes for a maintenance window before the session
    // gate's own check (and its adoption side effect) ever runs.
    single<SyncGate> {
        MaintenanceAwareSyncGate(session = get(named(QUALIFIER_SESSION_SYNC_GATE)), appStatus = get())
    }

    // Reads a stored file and puts it in a bucket. Only the two entities that name files
    // take one.
    single { BlobUploader(files = get(), storage = get(), telemetry = get()) }

    // The other direction: puts a bucket object back on this device, for a file whose row
    // synced here but whose bytes never did.
    single { BlobDownloader(files = get(), storage = get(), telemetry = get()) }

    single { ServiceLogRepositoryImpl(local = get(), telemetry = get(), scheduler = get()) }
    single<ServiceLogRepository> { get<ServiceLogRepositoryImpl>() }
    single { DocumentRepositoryImpl(local = get(), telemetry = get(), scheduler = get()) }
    single<DocumentRepository> { get<DocumentRepositoryImpl>() }
    // Score history, not today's score: the number on screen is computed on read, and
    // this only keeps what the month delta is measured against.
    single { HealthScoreRepositoryImpl(local = get(), telemetry = get(), scheduler = get()) }
    single<HealthScoreRepository> { get<HealthScoreRepositoryImpl>() }
    single<FairnessRepository> { FairnessRepositoryImpl(remote = get(), telemetry = get()) }
    // The one way to get a verdict. Any feature injects the port and gets the same
    // benchmarks, so no screen carries a benchmark table of its own.
    single<FairnessAnalyzer> { RepositoryFairnessAnalyzer(fairness = get()) }
    single {
        OverchargeReportRepositoryImpl(local = get(), telemetry = get(), idGenerator = get(), scheduler = get())
    }
    single<OverchargeReportRepository> { get<OverchargeReportRepositoryImpl>() }
    // Custom reminders + dismissals. The derived reminders have no rows — the feed
    // recomputes them — so this table only carries what cannot be recomputed.
    single {
        ReminderRepositoryImpl(local = get(), telemetry = get(), scheduler = get(), ids = get(), owners = get())
    }
    single<ReminderRepository> { get<ReminderRepositoryImpl>() }
    // The owner's city, read from their profile — null until they set it, which is what
    // keeps fairness silent rather than guessing.
    single<CurrentCityProvider> { ProfileCityProvider(local = get(), telemetry = get()) }

    // Remote data sources — the offline-safe defaults. `supabaseModule` is listed after this
    // module in `initKoin`, and replaces every one of these with a real adapter as soon as
    // the build carries Supabase credentials. Koin allows later definitions to override
    // earlier ones, so with no credentials the fakes simply stand.
    single<ServiceLogRemoteDataSource> { FakeServiceLogRemoteDataSource() }
    single<DocumentRemoteDataSource> { FakeDocumentRemoteDataSource() }
    single<FuelFillRemoteDataSource> { FakeFuelFillRemoteDataSource() }
    single<QuestionAnswerRemoteDataSource> { FakeQuestionAnswerRemoteDataSource() }
    single<FairnessRemoteDataSource> { FakeFairnessRemoteDataSource() }
    single<OverchargeRemoteDataSource> { FakeOverchargeRemoteDataSource() }
    single<ReminderRemoteDataSource> { FakeReminderRemoteDataSource() }
    single<RemoteFileStorage> { FakeRemoteFileStorage() }
    single<CarRemoteDataSource> { FakeCarRemoteDataSource() }
    single<VehicleCatalogRemoteDataSource> { FakeVehicleCatalogRemoteDataSource() }
    single<CityRemoteDataSource> { FakeCityRemoteDataSource() }
    single<EntitlementOverrideRemoteDataSource> { FakeEntitlementOverrideRemoteDataSource() }
    single<CitySubmissionRemoteDataSource> { FakeCitySubmissionRemoteDataSource() }
    single<ProfileRemoteDataSource> { FakeProfileRemoteDataSource() }
    single<HealthScoreRemoteDataSource> { FakeHealthScoreRemoteDataSource() }
    single<TripRemoteDataSource> { FakeTripRemoteDataSource() }
    // Not a data source, but the same swap and the same reason: a build with no credentials
    // has no server account, so erasing one is a no-op rather than a failure.
    single<AccountEraser> { OfflineAccountEraser() }
    // Development stub: it knows a couple of hardcoded plates so the "is this your
    // car?" path can be walked, and answers RegistrationNotFound for everything else.
    // MUST be swapped for a real adapter before launch — this one line is the swap.
    single<VehicleRegistryLookup> { StubVehicleRegistryLookup() }

    // What the owner's plan grants. Everyone is on the free plan until something sells a
    // subscription; :infrastructure:billing replaces this with the RevenueCat-backed source
    // on this one line, and every gate below follows without being touched.
    // Wrapped, not bare: an admin can grant Pro to a build with no billing behind it —
    // an internal tester, or a device where the store is unavailable — and that grant
    // has to be honoured by whichever source is standing.
    single<EntitlementSource> {
        OverridableEntitlementSource(store = FreePlanEntitlementSource(), overrides = get())
    }

    // The two counted gates, both reading the plan above. They keep their own ports because
    // their callers ask a shaped question ("how many documents", "how many scans left"), but
    // neither holds a number of its own any more — PlanLimits does.
    // Sign-in calls this unconditionally, so it is always bound. :infrastructure:billing
    // replaces it with the RevenueCat one when the build has a key.
    single<SubscriptionIdentity> { NoopSubscriptionIdentity() }

    single<DocumentAllowance> { EntitlementDocumentAllowance(entitlements = get()) }
    single<ScanAllowance> { EntitlementScanAllowance(entitlements = get(), usage = get()) }
    single<RecordExportUsage> { LocalRecordExportUsage(local = get(), clock = get()) }
    // Bought-and-unspent one-off exports (#246), beside the tally they are spent against.
    single<ExportCredits> { LocalExportCredits(local = get()) }
    // The tally the cap is measured against. Device-local: nothing counts a scan but the
    // phone that ran it.
    single<ScanUsage> { LocalScanUsage(local = get(), clock = get()) }

    // Extraction has no implementation yet, so both ports refuse and say why. A stub that
    // invented a bill would put made-up amounts into someone's service history, which is the
    // one thing this feature must never do. Swapping in the Edge Function callers is these
    // two lines.
    single<BillExtractor> { UnconfiguredBillExtractor() }
    single<DocumentExtractor> { UnconfiguredDocumentExtractor() }

    // Fuel fills. No Syncable adapter: there is no server table to push to yet, and one
    // posting to a table that does not exist would only manufacture failures. The rows
    // carry the sync columns and wait as PENDING.
    single<FuelFillRepository> { FuelFillRepositoryImpl(local = get(), telemetry = get(), scheduler = get()) }

    // Questionnaire answers (#394). The server table exists, so unlike fuel fills above this
    // one syncs; its Syncable is wired in `databaseInfrastructureModule`.
    single<QuestionnaireRepository> {
        QuestionnaireRepositoryImpl(
            local = get(),
            currentOwner = get(),
            telemetry = get(),
            scheduler = get(),
        )
    }

    // Blocks nothing until a real remote is configured. Swapped for
    // RemoteConfigAppStatusSource by :infrastructure:firebase:remoteconfig's Koin module,
    // which is registered after this one in initKoin — same later-definition-wins wiring
    // supabaseModule already relies on.
    single<AppStatusSource> { AlwaysAvailableAppStatusSource() }

    // Bound twice on purpose, exactly as supabaseModule binds LegalLinks. The qualified one
    // is what firebaseRemoteConfigModule resolves as the fallback behind the console's
    // answer; the unqualified one is what a build without that module still answers with.
    // Koin's later-wins override replaces only the second, so the fallback stays reachable.
    single<SupportContacts>(named(SupportContacts.BUILT_IN)) { BuiltInSupportContacts() }
    single<SupportContacts> { get(named(SupportContacts.BUILT_IN)) }
    single { AppStatusTelemetry(logger = get(), analytics = get(), tracer = get()) }
    single<AppStatusProvider> {
        DefaultAppStatusProvider(source = get(), appInfo = get(), clock = get(), telemetry = get())
    }
}
