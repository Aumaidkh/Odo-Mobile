package com.hopcape.odo.feature.auth

import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.auth.navigation.AuthFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the auth feature. `NavigationManager` comes from `coreNavigationModule`;
 * the `:app` host registers them all. [AuthFeatureEntryProvider] is bound to
 * [FeatureEntryProvider] so the host picks it up via `getAll<FeatureEntryProvider>()`.
 *
 * Auth also publishes [SessionStatusProvider] — the port other features ask before
 * offering to sign in. It is declared in `:core:domain`, so onboarding consumes it
 * without ever referencing `:feature:auth`.
 */
val authModule = module {
    single<SessionStatusProvider> { LocalSessionStatus() }
    single {
        AuthFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
