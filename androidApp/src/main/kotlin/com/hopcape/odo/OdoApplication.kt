package com.hopcape.odo

import android.app.Application
import com.hopcape.logging.api.HLogger
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.logging.api.loggerConfig
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
        // Configure the single logger first, so the Koin graph republishes a
        // ready logger (loggingModule binds HLogger.asLogger()).
        configureLogging(BuildConfig.DEBUG)
        initKoin(
            platformModule = module {
                single { DriverFactory(androidContext()) }
            },
        ) {
            androidLogger(Level.INFO)
            androidContext(this@OdoApplication)
        }
    }

    private fun configureLogging(isDebugBuild: Boolean) {
        HLogger.init(
            loggerConfig {
                environment(if (isDebugBuild) LoggerConfig.Environment.DEBUG else LoggerConfig.Environment.PRODUCTION)
                filePath("app_logs.log")
                remoteEndpoint(if (isDebugBuild) null.toString() else "https://logs.fourthfrontier.com/ingest")
                minLevel(if (isDebugBuild) LogLevel.VERBOSE else LogLevel.INFO)
                piiRedaction(true)
            }
        )
    }
}
