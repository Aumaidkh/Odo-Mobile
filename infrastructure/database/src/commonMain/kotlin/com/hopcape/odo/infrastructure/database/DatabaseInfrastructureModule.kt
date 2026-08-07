package com.hopcape.odo.infrastructure.database

import org.koin.dsl.module

/**
 * The SQLDelight database and the local-data-source adapters that implement the ports
 * `:core:data`'s repositories depend on.
 *
 * Listed **before** `coreSyncModule` in `initKoin` — the engine collects every registered
 * `Syncable` with `getAll()`, so the module that registers them has to run first.
 *
 * Empty for now: the database and its adapters still live in `:core:data` and move here in
 * a follow-up slice. Only this `val` is public — everything this module wires stays
 * `internal`, same as every other infrastructure module.
 */
val databaseInfrastructureModule = module {
}
