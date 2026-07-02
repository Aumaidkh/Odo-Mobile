package com.hopcape.odo

import androidx.compose.ui.window.ComposeUIViewController
import com.hopcape.logging.api.HLogger
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.odo.core.data.db.DriverFactory
import com.hopcape.odo.di.initKoin
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.dsl.module

private var koinStarted = false

/**
 * iOS Compose entry point. Ensures logging + the Koin graph are up before rendering
 * — the native SQLDelight [DriverFactory] needs no Context, so the platform module
 * is a one-liner. The [koinStarted] latch keeps a warm relaunch from starting twice.
 *
 * Logging is configured here (once) via [HLogger.init]; `loggingModule` then
 * republishes that same logger into the graph, mirroring the Android bootstrap.
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        val isDebug = Platform.isDebugBinary
        HLogger.init(
            LoggerConfig(
                environment = if (isDebug) LoggerConfig.Environment.DEBUG else LoggerConfig.Environment.PRODUCTION,
                filePath = "app_logs.log",
                minLevel = if (isDebug) LogLevel.VERBOSE else LogLevel.INFO,
            )
        )
        initKoin(platformModule = module { single { DriverFactory() } })
        koinStarted = true
    }
    App()
}
