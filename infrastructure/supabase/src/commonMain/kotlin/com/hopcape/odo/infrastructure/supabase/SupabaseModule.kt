package com.hopcape.odo.infrastructure.supabase

import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.city.CityRemoteDataSource
import com.hopcape.odo.core.data.entitlement.EntitlementOverrideRemoteDataSource
import com.hopcape.odo.core.data.city.CitySubmissionRemoteDataSource
import com.hopcape.odo.core.domain.advisory.BillLineClassifier
import com.hopcape.odo.core.domain.auth.AccountEraser
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.data.cost.FuelFillRemoteDataSource
import com.hopcape.odo.core.data.benchmark.FairnessContributionRemoteDataSource
import com.hopcape.odo.core.data.benchmark.PriceBandRemoteDataSource
import com.hopcape.odo.core.data.schedule.ServiceIntervalRemoteDataSource
import com.hopcape.odo.core.data.subscription.CreditSpendRemoteDataSource
import com.hopcape.odo.core.data.subscription.PurchaseClaimRemoteDataSource
import com.hopcape.odo.core.data.owner.QuestionAnswerRemoteDataSource
import com.hopcape.odo.core.data.document.DocumentRemoteDataSource
import com.hopcape.odo.core.data.challan.ChallanRemoteDataSource
import com.hopcape.odo.core.data.health.HealthScoreRemoteDataSource
import com.hopcape.odo.core.data.owner.ProfileRemoteDataSource
import com.hopcape.odo.core.data.fairness.FairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.core.data.reminder.ReminderRemoteDataSource
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.trip.TripRemoteDataSource
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.logging.api.LogUploadTarget
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseLegalLinks
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseAccountEraser
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseBillLineClassifier
import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.data.car.vehicleRegistryLookup
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseCarRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabasePlateRegistryLookup
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseVehicleRegistryLookup
import com.hopcape.odo.infrastructure.supabase.auth.DevPasswordAuthGateway
import com.hopcape.odo.infrastructure.supabase.auth.FirebaseBridgeAuthGateway
import com.hopcape.odo.infrastructure.supabase.auth.UnavailableAuthGateway
import com.hopcape.odo.infrastructure.supabase.auth.SupabaseTokenEndpoint
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseChallanRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseDocumentRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseFuelFillRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseCreditSpendRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseFairnessContributionRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabasePriceBandRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseServiceIntervalRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabasePurchaseClaimRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseQuestionAnswerRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseHealthScoreRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseLogUploader
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseProfileRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseFairnessRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseOverchargeRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseReminderRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseRemoteFileStorage
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseTripRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseCityRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseEntitlementOverrideRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseCitySubmissionRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseVehicleCatalogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.http.supabaseHttpClient
import com.hopcape.odo.infrastructure.supabase.http.supabaseHttpClientEngine
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Supabase adapters, bound over `:core:data`'s offline fakes.
 *
 * Listed **after** `coreDataModule` in `initKoin`. Koin lets a later definition replace an
 * earlier one, so each `single` below takes the place of the fake bound there — the swap the
 * data layer's comments have been describing since the ports were introduced. No repository
 * changes: they have always talked to the port, never to a client.
 *
 * **The swap only happens when the build has credentials.** A checkout with no
 * `supabase.url` / `supabase.anonKey` in `local.properties` generates blanks, [isConfigured]
 * is false, and none of the overrides are registered — so the fakes stand and the app runs
 * fully offline, which is its normal state anyway. Everything is a lazy `single`, so an
 * unconfigured build never even constructs an HTTP client.
 *
 * Only this `val` is public. The environment, the clients, the telemetry facade and every
 * adapter stay `internal`: the rest of the app knows the ports, not who implements them.
 */
val supabaseModule = supabaseModule(SupabaseEnvironment.fromBuild())

/**
 * The module above, for a given [environment] — the seam that lets a test drive both branches
 * instead of leaving the "is it configured" behaviour at the mercy of whoever's
 * `local.properties` the build ran against.
 */
internal fun supabaseModule(environment: SupabaseEnvironment) = module {

    single { environment }

    // One facade for every call that leaves the device. A `single`: it holds no per-call
    // state — the trace comes from the calling coroutine, not from this object.
    single { SupabaseTelemetry(logger = get(), tracer = get(), crash = get()) }

    // Outside the isConfigured branch below, unlike every other binding here: these are
    // three strings built from the project URL, with no client and nothing to call. An
    // unconfigured build gets blanks, which is what the screens check before offering a row.
    //
    // Bound twice on purpose. The qualified one is what `firebaseRemoteConfigModule` resolves
    // as the fallback behind the console-configured links; the unqualified one is what a
    // build without that module (or with Firebase unreachable at wiring time) still answers
    // with. Koin's later-wins override replaces only the second.
    single<LegalLinks>(named(LegalLinks.BUILT_IN)) { SupabaseLegalLinks(environment = get()) }
    single<LegalLinks> { get(named(LegalLinks.BUILT_IN)) }

    // The session itself lives in :feature:auth behind AccessTokenProvider. Only minting
    // one is a Supabase concern, and that is this:
    //
    // Which way in. Driven by `supabase.phoneAuth` in local.properties rather than a code
    // branch, so turning real sign-in on is a config edit and not a commit. With it off the
    // development account signs in, which still produces a real JWT under real row-level
    // security — everything downstream is verified for real.
    //
    // The real branch needs a PhoneVerifier, which firebaseAuthModule publishes earlier in
    // initKoin. Resolved lazily inside the single, so the Android bootstrap's override (bound
    // last, in the platform module) is the one that wins.
    if (environment.isConfigured) {
        // Erasing the account server-side. Inside the configured branch because it is a real
        // HTTP call — an unconfigured build has no account to erase, and the deletion flow
        // reads DomainError.NoVerifiedAccount and does the local wipe alone.
        single<AccountEraser> {
            SupabaseAccountEraser(client = get(), environment = get(), telemetry = get())
        }

        // The model naming bill lines the rules could not. Inside the configured branch for
        // the same reason as the eraser: it is a real HTTP call, and an unconfigured build
        // falls through to the offline classifier that names nothing.
        single<BillLineClassifier> {
            SupabaseBillLineClassifier(
                client = get(),
                environment = get(),
                tokens = get(),
                telemetry = get(),
            )
        }

        single { SupabaseTokenEndpoint(client = get(), environment = get(), telemetry = get()) }
    }

    // Outside the isConfigured branch, unlike the ports above it, and that difference is the
    // whole point. An unbound port there falls through to the offline fake `coreDataModule`
    // already registered; nothing registers an AuthGateway underneath this one, so leaving it
    // unbound left a hole instead of a fallback. `LateBoundAuthGateway` resolves per call, so
    // the hole surfaced as a fatal NoDefinitionFoundException the first time someone tapped
    // "Send code" — 1.3.3 on the internal track, Crashlytics 893bc4b1.
    //
    // Lazy, so the two configured branches still only reach for SupabaseTokenEndpoint (which
    // is inside that branch) on a build that has one.
    single<AuthGateway> {
        when {
            !environment.isConfigured -> UnavailableAuthGateway(telemetry = get())
            environment.usePhoneAuth -> FirebaseBridgeAuthGateway(verifier = get(), endpoint = get())
            else -> DevPasswordAuthGateway(endpoint = get())
        }
    }

    single<HttpClient> {
        supabaseHttpClient(
            engine = supabaseHttpClientEngine(),
            environment = get(),
            telemetry = get(),
        )
    }

    single {
        PostgrestClient(
            client = get(),
            environment = get(),
            tokens = get(),
            telemetry = get(),
        )
    }

    // Resolved at startup rather than on first use, because the whole point is to say
    // something before anyone wonders why sync is quiet.
    single(createdAtStart = true) { SupabaseReadiness(environment = get(), telemetry = get()) }

    if (environment.isConfigured) {
        single<ProfileRemoteDataSource> { SupabaseProfileRemoteDataSource(postgrest = get()) }
        single<CarRemoteDataSource> { SupabaseCarRemoteDataSource(postgrest = get()) }
        single<VehicleCatalogRemoteDataSource> { SupabaseVehicleCatalogRemoteDataSource(postgrest = get()) }
        single<CityRemoteDataSource> { SupabaseCityRemoteDataSource(postgrest = get()) }
        single<EntitlementOverrideRemoteDataSource> { SupabaseEntitlementOverrideRemoteDataSource(postgrest = get()) }
        single<CitySubmissionRemoteDataSource> { SupabaseCitySubmissionRemoteDataSource(postgrest = get()) }
        single<ServiceLogRemoteDataSource> { SupabaseServiceLogRemoteDataSource(postgrest = get()) }
        single<TripRemoteDataSource> { SupabaseTripRemoteDataSource(postgrest = get()) }
        single<HealthScoreRemoteDataSource> { SupabaseHealthScoreRemoteDataSource(postgrest = get()) }
        // TEMPORARILY parked on the Fake from coreDataModule: the challans tables
        // (supabase/migrations/20260822090000_challans.sql) are not applied to the
        // project yet, and a source that times out on every open is worse than sample
        // data. Uncomment once the migration has been run.
        // single<ChallanRemoteDataSource> { SupabaseChallanRemoteDataSource(postgrest = get()) }
        single<DocumentRemoteDataSource> { SupabaseDocumentRemoteDataSource(postgrest = get()) }
        single<FuelFillRemoteDataSource> { SupabaseFuelFillRemoteDataSource(postgrest = get()) }
        single<QuestionAnswerRemoteDataSource> { SupabaseQuestionAnswerRemoteDataSource(postgrest = get()) }
        single<PriceBandRemoteDataSource> { SupabasePriceBandRemoteDataSource(postgrest = get()) }
        single<FairnessContributionRemoteDataSource> {
            SupabaseFairnessContributionRemoteDataSource(postgrest = get())
        }
        single<ServiceIntervalRemoteDataSource> { SupabaseServiceIntervalRemoteDataSource(postgrest = get()) }
        single<PurchaseClaimRemoteDataSource> { SupabasePurchaseClaimRemoteDataSource(postgrest = get()) }
        single<CreditSpendRemoteDataSource> { SupabaseCreditSpendRemoteDataSource(postgrest = get()) }
        single<FairnessRemoteDataSource> { SupabaseFairnessRemoteDataSource(postgrest = get()) }
        single<OverchargeRemoteDataSource> { SupabaseOverchargeRemoteDataSource(postgrest = get()) }
        single<ReminderRemoteDataSource> { SupabaseReminderRemoteDataSource(postgrest = get()) }

        // The plate lookup, replacing StubVehicleRegistryLookup from coreDataModule (#392).
        //
        // Cheapest tier first. The cross-owner tier is the only one behind a flag, and it is
        // read at construction rather than per call: the chain is a `single`, and a flag flip
        // is a next-launch change, which is what a launch gate needs to be.
        single<VehicleRegistryLookup> {
            vehicleRegistryLookup(
                cars = get(),
                owners = get(),
                telemetry = get(),
                laterTiers = buildList {
                    add(SupabaseVehicleRegistryLookup(postgrest = get(), owners = get()))
                    if (get<FeatureConfig>().plateLookupEnabled) {
                        add(SupabasePlateRegistryLookup(postgrest = get()))
                    }
                },
            )
        }
        single<RemoteFileStorage> {
            SupabaseRemoteFileStorage(
                client = get(),
                environment = get(),
                tokens = get(),
                telemetry = get(),
            )
        }
        // Resolved by :observability:logging's loggingModule via getOrNull<LogUploadTarget>()
        // — an unconfigured build binds none, same as every adapter above (plan §7.1).
        single<LogUploadTarget> {
            SupabaseLogUploader(
                client = get(),
                environment = get(),
                tokens = get(),
                owners = get(),
                appInfo = get(),
                // The stable per-installation id the storage path and the index row are
                // grouped by. Bound by corePlatform{Android,Ios}Module.
                installationId = get(),
                postgrest = get(),
                telemetry = get(),
            )
        }
    }
}

/**
 * Says once, at startup, whether this build actually has a server to talk to.
 *
 * Without it an unconfigured build looks identical to a working one: the fakes accept every
 * push and report success, so sync appears healthy against a project that was never contacted.
 * A single warning line is the difference between that and an afternoon of confusion.
 */
internal class SupabaseReadiness(
    environment: SupabaseEnvironment,
    telemetry: SupabaseTelemetry,
) {
    init {
        if (!environment.isConfigured) telemetry.notConfigured()
    }
}
