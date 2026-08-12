package com.hopcape.odo.infrastructure.firebase.crashlytics

import com.hopcape.crashreporting.api.CrashSink

// ─────────────────────────────────────────────────────────────
// FirebaseCrashlyticsSink — the CrashSink adapter registered as an
// extra destination via CrashConfig.destinations (see
// OdoApplication.configureCrashReporting). Breadcrumbs become
// Crashlytics `log()` lines, custom keys become `setCustomKey`, and
// the throwable is reconstructed for `recordException` — CrashReport
// only ever carries the original exception as type/message/stack
// strings (it must outlive the process for a fatal report), so the
// live Throwable is gone by the time it reaches this sink.
//
// Public, unlike the rest of this module: CrashReporter.init(...) runs
// in OdoApplication.onCreate before the Koin graph starts, so
// :androidApp constructs this directly rather than resolving it
// through DI — the same reason :infrastructure:firebase:analytics's
// FirebaseAnalyticsSink is public. Only the public constructor's
// onDiagnostic param is part of that surface; the gateway seam stays
// internal for this module's own tests.
// ─────────────────────────────────────────────────────────────
class FirebaseCrashlyticsSink internal constructor(
    private val onDiagnostic: (String) -> Unit = {},
    private val gateway: FirebaseCrashlyticsGateway = RealFirebaseCrashlyticsGateway(onDiagnostic),
) : CrashSink {

    constructor(onDiagnostic: (String) -> Unit = {}) : this(
        onDiagnostic = onDiagnostic,
        gateway = RealFirebaseCrashlyticsGateway(onDiagnostic),
    )

    override val name: String = "crashlytics"

    override fun record(
        throwableType: String,
        throwableMessage: String?,
        stackTrace: String,
        isFatal: Boolean,
        breadcrumbs: List<String>,
        customKeys: Map<String, Any?>,
    ) {
        breadcrumbs.forEach(gateway::log)
        customKeys.forEach { (key, value) -> gateway.setCustomKey(key, value.toString()) }
        gateway.recordException(RuntimeException("$throwableType: $throwableMessage"))
    }

    override fun setCustomKey(key: String, value: Any?) {
        gateway.setCustomKey(key, value.toString())
    }

    override fun setUserId(userId: String?) {
        gateway.setUserId(userId)
    }
}
