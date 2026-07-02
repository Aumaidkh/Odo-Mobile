package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// CrashlyticsDestination — primary vendor (Firebase Crashlytics,
// alongside FCM). Thin adapter over the Firebase SDK; the real calls
// are sketched in comments (stub until the SDK is wired, exactly as
// the analytics module's FirebaseDestination).
//
// Note the mapping: breadcrumbs → Crashlytics `log()` lines, custom
// keys → `setCustomKey`, and the throwable → `recordException`. A
// fatal report re-uploaded on next launch is recorded the same way
// as a non-fatal — Crashlytics treats both as `recordException`.
// ─────────────────────────────────────────────────────────────
internal class CrashlyticsDestination(
    private val emit: (String) -> Unit = { kotlin.io.println(it) },
) : CrashDestination {

    override val name: String = "crashlytics"

    override fun record(report: CrashReport) {
        // FirebaseCrashlytics.getInstance().apply {
        //     report.breadcrumbs.forEach { log("${it.tag}: ${it.message}") }
        //     report.customKeys.forEach { (k, v) -> setCustomKey(k, v.toString()) }
        //     recordException(RuntimeException("${report.throwableType}: ${report.throwableMessage}"))
        // }
        emit("[Crashlytics] ${if (report.isFatal) "FATAL" else "non-fatal"}: ${report.throwableType}: ${report.throwableMessage}")
    }

    override fun setCustomKey(key: String, value: Any?) {
        // FirebaseCrashlytics.getInstance().setCustomKey(key, value.toString())
    }

    override fun setUserId(userId: String?) {
        // FirebaseCrashlytics.getInstance().setUserId(userId ?: "")
    }
}
