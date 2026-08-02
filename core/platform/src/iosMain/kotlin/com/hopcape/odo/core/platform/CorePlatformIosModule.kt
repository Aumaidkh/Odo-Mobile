package com.hopcape.odo.core.platform

import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.IosAppInfo
import com.hopcape.odo.core.platform.file.IosFileStore
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.notification.IosSystemNotificationSettings
import com.hopcape.odo.core.platform.notification.SystemNotificationSettings
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
    single<AppInfo> { IosAppInfo() }
    single<SystemNotificationSettings> { IosSystemNotificationSettings() }
}
