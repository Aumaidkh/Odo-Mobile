package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.BuildKonfig
import com.hopcape.odo.core.domain.appstatus.AppStatusSource
import org.koin.dsl.module

/**
 * Replaces `coreDataModule`'s [com.hopcape.odo.core.data.appstatus.AlwaysAvailableAppStatusSource]
 * with the real Firebase-backed one. Registered after `coreDataModule` in `initKoin`, the
 * same later-definition-wins wiring `supabaseModule` and `aiInfrastructureModule` already
 * rely on — moving this earlier silently puts the always-available stub back.
 *
 * The fetch interval is decided here, from [BuildKonfig.BUILD_TYPE] — the one global build
 * identity every module reads, not a value baked into this module's own Gradle config.
 */
val firebaseRemoteConfigModule = module {
    single<FirebaseRemoteConfigGateway> {
        val logger = get<Logger>()
        RealFirebaseRemoteConfigGateway(
            minimumFetchIntervalSeconds = minimumFetchIntervalSeconds(),
            defaults = RemoteConfigAppStatusSource.REMOTE_DEFAULTS,
            // Every other Firebase gateway in this repo reports failures the same way — a
            // vendor SDK failure is visible in logs, never a silent no-op and never a throw.
            onDiagnostic = { message -> logger.warn(TAG, message) },
        )
    }
    single<AppStatusSource> { RemoteConfigAppStatusSource(gateway = get()) }
}

/**
 * 1 minute on a debug build — a console change is visible on the next manual test. 1 hour
 * otherwise, so a real install fleet never hammers Remote Config's servers. Anything other
 * than `"debug"` (including a build type this module has never heard of) takes the
 * conservative branch — fail toward the safe interval, not the aggressive one.
 */
private fun minimumFetchIntervalSeconds(): Long = if (BuildKonfig.BUILD_TYPE == "debug") 60L else 3_600L

private const val TAG = "RemoteConfig"
