package com.hopcape.odo.feature.auth

import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.auth.SessionRestore
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.SignOut
import com.hopcape.odo.feature.auth.domain.LateBoundAuthGateway
import com.hopcape.odo.feature.auth.domain.OdoSessionManager
import com.hopcape.odo.feature.auth.domain.SignOutUseCase
import com.hopcape.odo.feature.auth.presentation.OtpViewModel
import com.hopcape.odo.feature.auth.presentation.PhoneViewModel
import org.koin.core.module.dsl.viewModel
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
    // The session's whole lifecycle: held here, persisted here, refreshed here. It answers
    // three domain ports, so nothing outside auth needs to know a session manager exists.
    single { AuthTelemetry(logger = get(), analytics = get()) }
    // The gateway is resolved per call, not captured here. This single is built by the
    // startup path before the first frame, so holding an instance would let build order
    // decide which way in the app uses — see LateBoundAuthGateway.
    single {
        OdoSessionManager(
            gateway = LateBoundAuthGateway { get() },
            store = get(),
            telemetry = get(),
            scheduler = get(),
            identity = get(),
            // The profile is where the owner's number is kept so it can reach the server —
            // auth is the only thing that knows it. Published from :core:data, so this
            // stays a domain port rather than a dependency on another feature.
            profiles = get(),
        )
    }
    single<SessionStatusProvider> { get<OdoSessionManager>() }
    single<CurrentOwnerProvider> { get<OdoSessionManager>() }
    single<AccessTokenProvider> { get<OdoSessionManager>() }
    // The startup path's one write. Without it a restart looks like a fresh install to the
    // sync gate, and nothing this install has ever written reaches the server.
    single<SessionRestore> { get<OdoSessionManager>() }
    // Published so :feature:profile can offer sign-out without depending on :feature:auth.
    single<SignOut> { SignOutUseCase(sessions = get(), wipe = get()) }

    viewModel { PhoneViewModel(sessions = get(), telemetry = get()) }
    // The number is a route argument, so it is a parameter rather than something the
    // ViewModel goes looking for.
    viewModel { (phone: PhoneNumber) -> OtpViewModel(phone = phone, sessions = get(), telemetry = get(), smsCodes = get(), smsSignature = get()) }

    single {
        AuthFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
