package com.hopcape.odo.core.platform

import android.content.Context
import com.hopcape.odo.core.platform.app.AndroidAppInfo
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.camera.AndroidDocumentCropper
import com.hopcape.odo.core.platform.camera.DocumentCropper
import com.hopcape.odo.core.platform.file.AndroidFileStore
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.notification.AndroidSystemNotificationSettings
import com.hopcape.odo.core.platform.secure.AndroidSecureStore
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.core.platform.sms.AndroidSmsAppSignature
import com.hopcape.odo.core.platform.sms.AndroidSmsCodeReader
import com.hopcape.odo.core.platform.sms.SmsAppSignature
import com.hopcape.odo.core.platform.sms.SmsCodeReader
import com.hopcape.odo.core.platform.sync.WorkManagerSyncScheduler
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.platform.notification.SystemNotificationSettings
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
    single<DocumentCropper> { AndroidDocumentCropper(context = get<Context>()) }
    single<AppInfo> { AndroidAppInfo(context = get<Context>()) }
    single<SystemNotificationSettings> { AndroidSystemNotificationSettings(context = get<Context>()) }
    single<SecureStore> { AndroidSecureStore(context = get<Context>()) }
    // Replaces :core:data's NoopSyncScheduler — the one line that turns the engine on.
    single<SyncScheduler> { WorkManagerSyncScheduler(context = get<Context>()) }
    single<SmsCodeReader> { AndroidSmsCodeReader(context = get<Context>()) }
    single<SmsAppSignature> { AndroidSmsAppSignature(context = get<Context>()) }
}
