package com.hopcape.odo.infrastructure.database.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** iOS driver factory — one declaration covers iosArm64 + iosSimulatorArm64. */
actual class DriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(OdoDatabase.Schema, "odo.db")
}
