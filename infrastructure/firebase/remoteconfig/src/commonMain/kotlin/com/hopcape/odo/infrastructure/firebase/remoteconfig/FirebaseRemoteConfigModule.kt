package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.appstatus.AppStatusSource
import org.koin.dsl.module

/**
 * Replaces `coreDataModule`'s [com.hopcape.odo.core.data.appstatus.AlwaysAvailableAppStatusSource]
 * with the real Firebase-backed one. Registered after `coreDataModule` in `initKoin`, the
 * same later-definition-wins wiring `supabaseModule` and `aiInfrastructureModule` already
 * rely on — moving this earlier silently puts the always-available stub back.
 *
 * [MINIMUM_FETCH_INTERVAL_SECONDS] is one flat production-safe value rather than a
 * debug/release split: the Remote Config SDK only throttles fetches that follow a prior
 * *successful* one, so a fresh install or a cleared app — the state manual QA actually
 * runs from — is never throttled regardless of this value.
 */
val firebaseRemoteConfigModule = module {
    single<FirebaseRemoteConfigGateway> {
        val logger = get<Logger>()
        RealFirebaseRemoteConfigGateway(
            minimumFetchIntervalSeconds = MINIMUM_FETCH_INTERVAL_SECONDS,
            defaults = RemoteConfigAppStatusSource.REMOTE_DEFAULTS,
            // Every other Firebase gateway in this repo reports failures the same way — a
            // vendor SDK failure is visible in logs, never a silent no-op and never a throw.
            onDiagnostic = { message -> logger.warn(TAG, message) },
        )
    }
    single<AppStatusSource> { RemoteConfigAppStatusSource(gateway = get()) }
}

private const val MINIMUM_FETCH_INTERVAL_SECONDS = 3_000L
private const val TAG = "RemoteConfig"
