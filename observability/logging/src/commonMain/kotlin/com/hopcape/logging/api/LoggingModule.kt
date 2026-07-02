package com.hopcape.logging.api

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI graph for logging — the module's Koin entry point alongside the [HLogger]
 * facade. Rather than building its own logger, it **republishes** the facade's
 * underlying [Logger] ([HLogger.asLogger]) into the graph. So DI consumers
 * (`koinInject<Logger>()` / constructor injection) and static `HLogger.tag(...)`
 * calls resolve to ONE configured logger — one config, one set of sinks, one file.
 *
 * Configuration therefore lives in exactly one place: the app's single
 * [HLogger.init] call at startup (which picks debug vs production by build type).
 * Callers only ever see the [Logger] interface; every sink/redactor stays
 * `internal` to this module.
 */
val loggingModule: Module = module {
    single<Logger> { HLogger.asLogger() }
}
