import com.hopcape.odo.core.platform.bluetooth.IosSystemBluetoothSettings
import com.hopcape.odo.core.platform.bluetooth.SystemBluetoothSettings
package com.hopcape.odo.core.platform

import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.IosAppInfo
import com.hopcape.odo.core.platform.camera.DocumentCropper
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.IosFileStore
import com.hopcape.odo.core.platform.file.PlatformDownloads
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StoredPageRenderer
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import com.hopcape.odo.core.platform.notification.BackgroundStartAccess
import com.hopcape.odo.core.platform.notification.IosBackgroundStartAccess
import com.hopcape.odo.core.platform.notification.IosDetectedFillNotifier
import com.hopcape.odo.core.platform.notification.IosNotificationAccess
import com.hopcape.odo.core.platform.notification.DetectedFillNotifier
import com.hopcape.odo.core.platform.notification.NotificationAccess
import com.hopcape.odo.core.platform.notification.PaymentNotices
import com.hopcape.odo.core.domain.refuel.PaymentNoticeSource
import com.hopcape.odo.core.platform.notification.IosSystemNotificationSettings
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import com.hopcape.odo.core.platform.secure.IosSecureStore
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.core.platform.showcase.DefaultsShowcaseSeenStore
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
    // No Files-app export on iOS yet. Refusing says so through the screen's own "could not
    // save" message, which is the same thing the owner would see if the disk were full.
    single<PlatformDownloads> {
        PlatformDownloads { _, _, _ -> DomainError.PersistenceFailure("downloads are not available on iOS yet").left() }
    }
    // No PDF rasterizer on iOS yet. Answering "no page" leaves the reader's own
    // "we could not read this" message doing the explaining, as it does for a bad photo.
    single<StoredPageRenderer> { StoredPageRenderer { null } }
    // No edge detection on iOS yet, so there is never a quad to crop to — the photo
    // passes through untouched.
    single<DocumentCropper> { DocumentCropper { storageKey, _ -> storageKey } }
    // No still-image reader on iOS yet — the live preview is the only way in. Answering
    single<AppInfo> { IosAppInfo() }
    single<SystemNotificationSettings> { IosSystemNotificationSettings() }
    single<NotificationAccess> { IosNotificationAccess() }
    single<DetectedFillNotifier> { IosDetectedFillNotifier() }
    single<BackgroundStartAccess> { IosBackgroundStartAccess() }
    single<SystemBluetoothSettings> { IosSystemBluetoothSettings() }
    // Nothing ever publishes into it on iOS — there is no way to read another app's
    // notifications — so the flow simply never emits.
    single<PaymentNoticeSource> { PaymentNotices }
    // No local notification scheduling on iOS yet; the reminders screen still derives and
    // shows every expiry, so nothing is lost beyond the push itself.
    single<DocumentReminderScheduler> { DocumentReminderScheduler { } }
    // Unlike the file store, this one is real: the Keychain needs nothing Phase 2 has not
    // already shipped, and a session has to survive a relaunch on iOS as much as on Android.
    single<SecureStore> { IosSecureStore() }
    single<ShowcaseSeenStore> { DefaultsShowcaseSeenStore() }

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
