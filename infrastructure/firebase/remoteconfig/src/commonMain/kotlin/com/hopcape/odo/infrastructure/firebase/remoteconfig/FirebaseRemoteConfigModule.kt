package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.BuildInfo
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.core.domain.appstatus.AppStatusSource
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.core.domain.support.SupportContacts
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Replaces `coreDataModule`'s [com.hopcape.odo.core.data.appstatus.AlwaysAvailableAppStatusSource]
 * with the real Firebase-backed one. Registered after `coreDataModule` in `initKoin`, the
 * same later-definition-wins wiring `supabaseModule` and `aiInfrastructureModule` already
 * rely on — moving this earlier silently puts the always-available stub back.
 *
 * The fetch interval is decided here, from [BuildInfo.isDebug] — the one global build
 * identity every module reads, not a value baked into this module's own Gradle config.
 */
val firebaseRemoteConfigModule = module {

    // The generated Koin modules for the three groups in RemoteConfigKeys.kt. Included
    // here rather than listed in initKoin, so declaring a key stays a one-file change.
    includes(appStatusConfigModule, legalConfigModule, supportConfigModule)

    single<FirebaseRemoteConfigGateway> {
        val logger = get<Logger>()
        RealFirebaseRemoteConfigGateway(
            minimumFetchIntervalSeconds = minimumFetchIntervalSeconds(),
            // Every registered key's compiled default, in one call — the SDK takes
            // defaults once per process. This comes from the registry now, so a new key
            // is declared once and seeds the SDK by existing. It used to be three
            // hand-maintained maps summed here, plus a fourth copy in an XML resource.
            defaults = get<ConfigRegistry>().defaults(),
            // Every other Firebase gateway in this repo reports failures the same way — a
            // vendor SDK failure is visible in logs, never a silent no-op and never a throw.
            onDiagnostic = { message -> logger.warn(TAG, message) },
        )
    }
    single<AppStatusSource> {
        RemoteConfigAppStatusSource(gateway = get(), config = get(), refresher = get())
    }

    // Replaces coreConfigModule's NoRemoteConfigSource and ConfigRefresher.None, the same
    // later-definition-wins wiring as the AppStatusSource above.
    //
    // One instance bound to both interfaces, not two definitions: the generation counter
    // lives in it, so a second instance would fetch on one object and leave every flow
    // watching the other.
    single { RemoteConfigSource(gateway = get()) } binds
        arrayOf(ConfigSource::class, ConfigRefresher::class)

    // Replaces supabaseModule's build-time links, and resolves that one as its fallback —
    // hence the qualifier. Same later-wins wiring as the AppStatusSource above, with one
    // difference worth knowing: this decorates rather than discards, so a device that never
    // reaches Firebase keeps working links instead of losing the privacy policy entirely.
    single<LegalLinks> {
        RemoteConfigLegalLinks(config = get(), builtIn = get(named(LegalLinks.BUILT_IN)))
    }

    // Decorates coreDataModule's compiled-in address the same way, and for a sharper reason:
    // a support mailbox that moves leaves every installed build mailing a dead address.
    single<SupportContacts> {
        RemoteConfigSupportContacts(config = get(), builtIn = get(named(SupportContacts.BUILT_IN)))
    }
}

/**
 * 1 minute on a debug build — a console change is visible on the next manual test. 1 hour
 * otherwise, so a real install fleet never hammers Remote Config's servers.
 */
private fun minimumFetchIntervalSeconds(): Long = if (BuildInfo.isDebug) 60L else 3_600L

private const val TAG = "RemoteConfig"
