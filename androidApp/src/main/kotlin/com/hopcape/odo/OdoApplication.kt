package com.hopcape.odo

import android.app.Application
import com.hopcape.odo.core.data.db.DriverFactory
import com.hopcape.odo.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level
import org.koin.dsl.module

/**
 * Android entry point: starts the Koin graph before the first Activity. It supplies
 * the two things only the platform can — the application [android.content.Context]
 * (via `androidContext()`) and the Context-bearing SQLDelight [DriverFactory] — and
 * lets the shared [initKoin] wire the rest (navigation, data layer, features).
 */
class OdoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            platformModule = module {
                single { DriverFactory(androidContext()) }
            },
        ) {
            androidLogger(Level.INFO)
            androidContext(this@OdoApplication)
        }
    }
}
