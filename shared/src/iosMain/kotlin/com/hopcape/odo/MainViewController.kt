package com.hopcape.odo

import androidx.compose.ui.window.ComposeUIViewController
import com.hopcape.odo.core.data.db.DriverFactory
import com.hopcape.odo.di.initKoin
import org.koin.dsl.module

private var koinStarted = false

/**
 * iOS Compose entry point. Ensures the Koin graph is up before rendering — the
 * native SQLDelight [DriverFactory] needs no Context, so the platform module is a
 * one-liner. The [koinStarted] latch keeps a warm relaunch from starting it twice.
 */
fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        initKoin(platformModule = module { single { DriverFactory() } })
        koinStarted = true
    }
    App()
}
