package com.hopcape.odo.di

import com.hopcape.logging.api.loggingModule
import com.hopcape.odo.core.data.coreDataModule
import com.hopcape.odo.core.navigation.coreNavigationModule
import com.hopcape.odo.feature.onboarding.onboardingModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts the app's Koin graph — the single composition-root entry point every
 * platform bootstrap calls exactly once before the first frame.
 *
 * The **shared** modules (navigation bus, data layer, and each wired feature) are
 * assembled here; platform-specific bits come in through [platformModule] and
 * [declaration]:
 *
 *  - Android — `OdoApplication` passes `androidContext()` + a module supplying the
 *    Context-bearing SQLDelight `DriverFactory`.
 *  - iOS — `MainViewController` passes the native `DriverFactory` module.
 *
 * Logging is configured out-of-band by the platform's single `HLogger.init(...)`
 * call (which picks debug vs production by build type); [loggingModule] only
 * republishes that one logger into the graph, so no build-type flag is needed here.
 *
 * Adding a feature to the running app is then two lines: `implementation` its
 * module here and list its Koin module below — `App()` discovers its screens via
 * `getAll<FeatureEntryProvider>()`, so no other file changes.
 */
fun initKoin(
    platformModule: Module,
    declaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    declaration()
    modules(
        loggingModule,
        coreNavigationModule,
        coreDataModule,
        onboardingModule,
        platformModule,
    )
}
