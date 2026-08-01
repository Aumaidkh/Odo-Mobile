package com.hopcape.odo.core.platform

import com.hopcape.odo.core.platform.file.IosFileStore
import com.hopcape.odo.core.platform.file.PlatformFileStore
import org.koin.dsl.module

/**
 * The iOS-only platform bindings, registered by `MainViewController`. The mirror of
 * [corePlatformAndroidModule].
 *
 * The store it binds refuses every write (Phase 2 builds the real one), which is still
 * better than leaving the port unbound: a missing definition fails as a Koin crash on a
 * screen that has nothing to do with storage.
 */
val corePlatformIosModule = module {
    single<PlatformFileStore> { IosFileStore() }
}
