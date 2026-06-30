package com.hopcape.odo.core.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android driver factory — needs an application [Context], supplied by Koin
 * (`androidContext()`) from the `:app` bootstrap.
 */
actual class DriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(OdoDatabase.Schema, context, "odo.db")
}
