package com.hopcape.odo.infrastructure.supabase

import com.hopcape.odo.core.data.document.DocumentRemoteDataSource
import com.hopcape.odo.core.data.fairness.FairnessRemoteDataSource
import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseDocumentRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseFairnessRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseOverchargeRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseRemoteFileStorage
import com.hopcape.odo.infrastructure.supabase.adapters.SupabaseServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.http.AnonAccessTokens
import com.hopcape.odo.infrastructure.supabase.http.SupabaseAccessTokens
import com.hopcape.odo.infrastructure.supabase.http.supabaseHttpClient
import com.hopcape.odo.infrastructure.supabase.http.supabaseHttpClientEngine
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import io.ktor.client.HttpClient
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

    // Anon until Supabase phone auth lands (M5). THIS ONE LINE is that swap.
    single<SupabaseAccessTokens> { AnonAccessTokens(environment = get()) }

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
        single<ServiceLogRemoteDataSource> { SupabaseServiceLogRemoteDataSource(postgrest = get()) }
        single<DocumentRemoteDataSource> { SupabaseDocumentRemoteDataSource(postgrest = get()) }
        single<FairnessRemoteDataSource> { SupabaseFairnessRemoteDataSource(postgrest = get()) }
        single<OverchargeRemoteDataSource> { SupabaseOverchargeRemoteDataSource(postgrest = get()) }
        single<RemoteFileStorage> {
            SupabaseRemoteFileStorage(
                client = get(),
                environment = get(),
                tokens = get(),
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
