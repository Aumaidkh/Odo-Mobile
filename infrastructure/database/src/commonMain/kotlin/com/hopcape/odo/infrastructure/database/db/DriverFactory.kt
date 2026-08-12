package com.hopcape.odo.infrastructure.database.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform [SqlDriver] for [OdoDatabase]. The `actual`s live in
 * `androidMain` (AndroidSqliteDriver, needs a Context) and `iosMain`
 * (NativeSqliteDriver). The Context-bearing Android factory is provided by the
 * `:app` DI bootstrap; this module only declares the contract and the common
 * `OdoDatabase` wiring.
 */
expect class DriverFactory {
    fun create(): SqlDriver
}

/** Build the typed database from a platform driver. */
fun createOdoDatabase(driver: SqlDriver): OdoDatabase = OdoDatabase(driver)
