package com.hopcape.odo.core.platform

import android.content.Context
import com.hopcape.odo.core.platform.app.AndroidAppInfo
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.camera.AndroidDocumentCropper
import com.hopcape.odo.core.platform.camera.AndroidQrImageDecoder
import com.hopcape.odo.core.platform.camera.DocumentCropper
import com.hopcape.odo.core.platform.camera.QrImageDecoder
import com.hopcape.odo.core.platform.file.AndroidFileStore
import com.hopcape.odo.core.platform.file.AndroidStoredPageRenderer
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StoredPageRenderer
import com.hopcape.odo.core.platform.logging.AndroidLogFileStore
import com.hopcape.odo.core.platform.logging.WorkManagerLogUploadScheduler
import com.hopcape.odo.core.platform.notification.AndroidSystemNotificationSettings
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import com.hopcape.odo.core.platform.notification.WorkManagerDocumentReminderScheduler
import com.hopcape.odo.core.platform.secure.AndroidSecureStore
import com.hopcape.odo.core.platform.secure.SecureStore
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
    single<StoredPageRenderer> { AndroidStoredPageRenderer(context = get<Context>()) }
    single<DocumentCropper> { AndroidDocumentCropper(context = get<Context>()) }
    single<QrImageDecoder> { AndroidQrImageDecoder(context = get<Context>()) }
    single<AppInfo> { AndroidAppInfo(context = get<Context>()) }
    single<SystemNotificationSettings> { AndroidSystemNotificationSettings(context = get<Context>()) }
    // Turns a document's expiry into notifications the OS delivers. Replaces :core:data's
    // no-op binding — the one line that makes a saved expiry actually reach the owner.
    single<DocumentReminderScheduler> {
        WorkManagerDocumentReminderScheduler(
            context = get<Context>(),
            documents = get(),
            activeCar = get(),
            clock = get(),
            logger = get(),
        )
    }
    single<SecureStore> { AndroidSecureStore(context = get<Context>()) }
    // Replaces :core:data's NoopSyncScheduler — the one line that turns the engine on.
    single<SyncScheduler> { WorkManagerSyncScheduler(context = get<Context>()) }
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
