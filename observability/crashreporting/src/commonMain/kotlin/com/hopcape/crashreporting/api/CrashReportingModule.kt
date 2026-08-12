package com.hopcape.crashreporting.api

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI graph for crash reporting — the module's Koin entry point alongside the
 * [CrashReporter] facade. Rather than building its own recorder, it
 * **republishes** the facade's underlying [CrashRecorder] ([CrashReporter.asRecorder]).
 * So DI consumers (`koinInject<CrashRecorder>()` / constructor injection) and
 * static `CrashReporter.recordNonFatal(...)` calls resolve to ONE configured
 * recorder — one config, one set of destinations, one file store.
 *
 * Configuration therefore lives in exactly one place: the app's single
 * [CrashReporter.init] call at startup. Callers only ever see the [CrashRecorder]
 * interface; every destination / redactor / file store stays `internal`.
 */
val crashReportingModule: Module = module {
    single<CrashRecorder> { CrashReporter.asRecorder() }
}
