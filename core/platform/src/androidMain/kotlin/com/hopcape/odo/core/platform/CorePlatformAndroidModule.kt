package com.hopcape.odo.core.platform

import android.content.Context
import com.hopcape.odo.core.platform.file.AndroidFileStore
import com.hopcape.odo.core.platform.file.PlatformFileStore
import org.koin.dsl.module

/**
 * The Android-only platform bindings, registered by the `:app` bootstrap.
 *
 * Separate from the shared graph because storing a file needs a `Context`, and that is the
 * one thing common code cannot ask for. Koin already holds it — `androidContext()` in the
 * bootstrap declares it — so this resolves the Context rather than taking it as a
 * parameter, and the app's only job is to list the module.
 */
val corePlatformAndroidModule = module {
    single<PlatformFileStore> { AndroidFileStore(context = get<Context>()) }
}
