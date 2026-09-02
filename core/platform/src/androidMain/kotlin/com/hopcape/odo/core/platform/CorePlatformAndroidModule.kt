package com.hopcape.odo.core.platform

import android.app.Application
import android.content.Context
import com.hopcape.odo.core.platform.app.ActivityTracker
import com.hopcape.odo.core.platform.app.AndroidAppInfo
import com.hopcape.odo.core.platform.app.AndroidDeviceInfo
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.DeviceInfo
import com.hopcape.odo.core.platform.app.InstallationId
import com.hopcape.odo.core.platform.app.PrefsInstallationId
import com.hopcape.odo.core.platform.app.CurrentActivity
import com.hopcape.odo.core.platform.camera.AndroidDocumentCropper
import com.hopcape.odo.core.platform.camera.DocumentCropper
import com.hopcape.odo.core.platform.file.AndroidDownloads
import com.hopcape.odo.core.platform.file.AndroidFileStore
import com.hopcape.odo.core.platform.file.AndroidStoredPageRenderer
import com.hopcape.odo.core.platform.file.PlatformDownloads
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StoredPageRenderer
import com.hopcape.odo.core.platform.logging.AndroidLogFileStore
import com.hopcape.odo.core.platform.logging.WorkManagerLogUploadScheduler
import com.hopcape.odo.core.platform.notification.AndroidBackgroundStartAccess
import com.hopcape.odo.core.platform.notification.AndroidDetectedFillNotifier
import com.hopcape.odo.core.platform.notification.BackgroundStartAccess
import com.hopcape.odo.core.platform.notification.AndroidNotificationAccess
import com.hopcape.odo.core.platform.notification.DetectedFillNotifier
import com.hopcape.odo.core.platform.notification.NotificationAccess
import com.hopcape.odo.core.platform.notification.PaymentNotices
import com.hopcape.odo.core.domain.refuel.PaymentNoticeSource
import com.hopcape.odo.core.platform.notification.AndroidSystemNotificationSettings
import com.hopcape.odo.core.platform.notification.CustomReminderScheduler
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import com.hopcape.odo.core.platform.notification.WorkManagerCustomReminderScheduler
import com.hopcape.odo.core.platform.notification.WorkManagerDocumentReminderScheduler
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import com.hopcape.odo.core.platform.secure.AndroidSecureStore
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.core.config.LocalConfigOverrides
import com.hopcape.odo.core.common.BuildInfo
import com.hopcape.odo.core.config.ConfigSnapshotStore
import com.hopcape.odo.core.platform.config.PrefsConfigSnapshotStore
import com.hopcape.odo.core.platform.config.PrefsLocalConfigOverrides
import com.hopcape.odo.core.platform.showcase.PrefsShowcaseSeenStore
import com.hopcape.odo.core.platform.sms.AndroidSmsAppSignature
import com.hopcape.odo.core.platform.sms.AndroidSmsCodeReader
import com.hopcape.odo.core.platform.sms.SmsAppSignature
import com.hopcape.odo.core.platform.sms.SmsCodeReader
import com.hopcape.odo.core.platform.sync.WorkManagerSyncScheduler
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.platform.notification.SystemNotificationSettings
import com.hopcape.logging.api.LogFileStore
import com.hopcape.logging.api.LogUploadScheduler
import org.koin.dsl.module
import java.io.File

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
    single<PlatformDownloads> { AndroidDownloads(context = get<Context>()) }
    single<StoredPageRenderer> { AndroidStoredPageRenderer(context = get<Context>()) }
    single<DocumentCropper> { AndroidDocumentCropper(context = get<Context>()) }
    single<AppInfo> { AndroidAppInfo(context = get<Context>()) }
    // Needs no Context: the model and OS version are static Build fields.
    single<DeviceInfo> { AndroidDeviceInfo() }

    // One id per installation, generated on first read. Diagnostics group by it, and the
    // reference code on a support mail is derived from it (docs/LOGGING_PLAN.md).
    single<InstallationId> { PrefsInstallationId(context = get<Context>()) }
    // createdAtStart, because it starts watching in its constructor: resolved on first use it
    // would begin listening after the Activity it is being asked about already resumed. See
    // ActivityTracker's comment.
    single<CurrentActivity>(createdAtStart = true) {
        ActivityTracker(application = get<Context>().applicationContext as Application)
    }
    single<SystemNotificationSettings> { AndroidSystemNotificationSettings(context = get<Context>()) }
    single<NotificationAccess> { AndroidNotificationAccess(context = get<Context>()) }
    single<DetectedFillNotifier> { AndroidDetectedFillNotifier(context = get<Context>()) }
    single<BackgroundStartAccess> { AndroidBackgroundStartAccess(context = get<Context>()) }
    // The listener service is constructed by the OS and cannot be injected, so the object it
    // publishes into is what the graph knows about.
    single<PaymentNoticeSource> { PaymentNotices }
    // Turns a document's expiry into notifications the OS delivers. Replaces :core:data's
    // no-op binding — the one line that makes a saved expiry actually reach the owner.
    single<DocumentReminderScheduler> {
        WorkManagerDocumentReminderScheduler(
            context = get<Context>(),
            documents = get(),
            activeCar = get(),
            settings = get(),
            clock = get(),
            logger = get(),
        )
    }
    // Replaces :core:data's no-op — the line that turns the owner's own reminders from a
    // list they have to open the app to see into notifications that arrive.
    single<CustomReminderScheduler> {
        WorkManagerCustomReminderScheduler(
            context = get<Context>(),
            reminders = get(),
            activeCar = get(),
            settings = get(),
            clock = get(),
            logger = get(),
        )
    }
    single<SecureStore> { AndroidSecureStore(context = get<Context>()) }
    // Which coach marks have been seen — prefs, not the database, so nothing to migrate
    // (docs/SHOWCASE_PLAN.md decision 1). The arbiter that reads it is bound in :core:data.
    single<ShowcaseSeenStore> { PrefsShowcaseSeenStore(context = get<Context>()) }

    // Debug builds only. ConfigResolver takes this with getOrNull, so a release build finds
    // nothing behind the first step of the resolution order rather than taking a different
    // path through it — the branch is not compiled out, there is simply no store.
    if (BuildInfo.isDebug) {
        single<LocalConfigOverrides> { PrefsLocalConfigOverrides(context = get<Context>()) }
    }
    // Release too, unlike the overrides above: this is not a QA affordance, it is what
    // lets a cold start resolve last launch's remote values instead of falling back to
    // compiled defaults until the first fetch lands.
    single<ConfigSnapshotStore> { PrefsConfigSnapshotStore(context = get<Context>()) }
    // Replaces :core:data's NoopSyncScheduler — the one line that turns the engine on.
    single<SyncScheduler> { WorkManagerSyncScheduler(context = get<Context>(), telemetry = get()) }
    single<SmsCodeReader> { AndroidSmsCodeReader(context = get<Context>()) }
    single<SmsAppSignature> { AndroidSmsAppSignature(context = get<Context>()) }
    // The durable file sink's on-disk store (docs/LOGGING_PLAN.md). "logs" alongside
    // AndroidFileStore's own subdirectories under filesDir — app-private, but NOT yet
    // excluded from Android's Auto Backup: the manifest has no dataExtractionRules at all
    // today (same gap applies to CrashReporter's "crash" dir). Owed as a follow-up; log
    // lines already pass through PII redaction before they're written, so this is a
    // defense-in-depth gap, not an unredacted-PII one.
    //
    // This is a SEPARATE instance from the one OdoApplication.configureLogging() builds and
    // hands to HLogger.init() — that one has to exist before Koin starts (same reason
    // FirebaseCrashlyticsSink is constructed directly there), so it can't be resolved from
    // here. Both point at the same directory and agree via the filesystem, which is safe for
    // every method except sealOrphans(): that one must only ever be called on the logger's
    // own instance, at startup, before it writes anything — never on this one. Consumers
    // resolved through Koin (the upload coordinator, "send diagnostics") only ever read,
    // list, and delete sealed files, so they never touch that method.
    single<LogFileStore> { AndroidLogFileStore(dir = File(get<Context>().filesDir, "logs")) }
    single<LogUploadScheduler> { WorkManagerLogUploadScheduler(context = get<Context>()) }
}
