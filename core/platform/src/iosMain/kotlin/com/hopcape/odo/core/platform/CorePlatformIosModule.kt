package com.hopcape.odo.core.platform

import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.IosAppInfo
import com.hopcape.odo.core.platform.camera.DocumentCropper
import com.hopcape.odo.core.platform.camera.QrImageDecoder
import com.hopcape.odo.core.platform.file.IosFileStore
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.notification.IosSystemNotificationSettings
import com.hopcape.odo.core.platform.secure.IosSecureStore
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.core.platform.sms.IosSmsAppSignature
import com.hopcape.odo.core.platform.sms.IosSmsCodeReader
import com.hopcape.odo.core.platform.sms.SmsAppSignature
import com.hopcape.odo.core.platform.sms.SmsCodeReader
import com.hopcape.odo.core.platform.sync.CoroutineSyncScheduler
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    // No edge detection on iOS yet, so there is never a quad to crop to — the photo
    // passes through untouched.
    single<DocumentCropper> { DocumentCropper { storageKey, _ -> storageKey } }
    // No still-image reader on iOS yet — the live preview is the only way in. Answering
    // "no code here" leaves the screen's own message doing the explaining.
    single<QrImageDecoder> { QrImageDecoder { null } }
    single<AppInfo> { IosAppInfo() }
    single<SystemNotificationSettings> { IosSystemNotificationSettings() }
    // Unlike the file store, this one is real: the Keychain needs nothing Phase 2 has not
    // already shipped, and a session has to survive a relaunch on iOS as much as on Android.
    single<SecureStore> { IosSecureStore() }

    // iOS has no WorkManager, so sync runs in-process on an app-lifetime scope. That covers
    // every foreground trigger — launch, a local write, pull-to-refresh — which is what
    // makes sync work on iOS today. What it does not cover is running after the app is
    // backgrounded; that needs BGTaskScheduler plus an Info.plist identifier, and is the
    // one piece of iOS sync still outstanding.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<SyncScheduler> { CoroutineSyncScheduler(engine = { get() }, scope = get()) }
    // Unsupported on purpose: the iOS keyboard already offers the code from the notification.
    single<SmsCodeReader> { IosSmsCodeReader() }
    single<SmsAppSignature> { IosSmsAppSignature() }
}
