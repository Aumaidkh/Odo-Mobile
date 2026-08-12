package com.hopcape.odo.core.sync

import com.hopcape.odo.core.sync.observability.SyncTelemetry
import org.koin.dsl.module

/**
 * The engine and its telemetry.
 *
 * `getAll<Syncable>()` is the whole point of the seam: the engine receives every registered
 * `Syncable` rather than reaching into `:core:data` to find them, which is what stops
 * `:core:data` ↔ `:core:sync` becoming a dependency cycle (SYNC_DESIGN §5.1). Adding a
 * seventh syncable table is a binding in that module and nothing here.
 *
 * [SyncGate] and [Synchronizer] are bound elsewhere — the gate by whoever owns sessions,
 * the synchronizer by `:core:data`, which is the only module with a database.
 */
val coreSyncModule = module {

    single { SyncTelemetry(logger = get(), analytics = get(), tracer = get(), crash = get()) }

    single<SyncEngine> {
        DefaultSyncEngine(
            syncables = getAll<Syncable>(),
            synchronizer = get(),
            telemetry = get(),
            gate = get(),
            observer = get(),
        )
    }
}
