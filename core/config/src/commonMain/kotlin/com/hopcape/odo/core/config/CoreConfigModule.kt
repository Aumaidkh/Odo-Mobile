package com.hopcape.odo.core.config

import org.koin.dsl.module

/**
 * The registry and the resolver, plus the answers used when nothing remote is wired.
 *
 * **Ordering.** List this after every module that registers a [ConfigContribution], and
 * before any module that supplies a real [ConfigSource] — `firebaseRemoteConfigModule`
 * replaces both bindings below, and in Koin the later definition wins.
 *
 * [LocalConfigOverrides] is resolved with `getOrNull`, so a release build simply has no
 * store behind it. Nothing about the resolution order changes between variants; there is
 * only nothing to find.
 */
val coreConfigModule = module {

    // Replaced by the Firebase adapter. Not a stub to delete later: this is the correct
    // answer for a build with no backend, and it keeps every key on its compiled default
    // instead of failing.
    single<ConfigSource> { NoRemoteConfigSource }
    single<ConfigRefresher> { ConfigRefresher.None }

    // getAll, so a module that declares config contributes by being installed and nothing
    // has to maintain a list. Resolved lazily, after every module is loaded.
    single { ConfigRegistry(getAll<ConfigContribution>()) }

    single { ConfigResolver(registry = get(), source = get(), overrides = getOrNull()) }
}
