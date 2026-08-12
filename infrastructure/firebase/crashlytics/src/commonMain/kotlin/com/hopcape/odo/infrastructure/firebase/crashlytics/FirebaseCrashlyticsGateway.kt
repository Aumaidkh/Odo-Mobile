package com.hopcape.odo.infrastructure.firebase.crashlytics

import com.hopcape.odo.core.common.runCatchingCancellable
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.FirebaseCrashlytics
import dev.gitlive.firebase.crashlytics.crashlytics

// ─────────────────────────────────────────────────────────────
// FirebaseCrashlyticsGateway — the real Firebase SDK surface this module
// calls, narrowed to what a future CrashDestination adapter needs. The
// seam exists because gitlive's FirebaseCrashlytics is a concrete SDK
// class (an expect class), not an interface, so nothing about it can be
// faked directly in a test — this is the fake-able boundary instead.
// Mirrors :infrastructure:firebase:analytics's FirebaseAnalyticsGateway.
// ─────────────────────────────────────────────────────────────
internal interface FirebaseCrashlyticsGateway {
    /** Records a handled or reconstructed-fatal exception. */
    fun recordException(throwable: Throwable)

    /** Adds a log line included with the next report (Crashlytics' breadcrumb equivalent). */
    fun log(message: String)

    /** Sets a custom key attached to subsequent reports. */
    fun setCustomKey(key: String, value: String)

    /** Associates subsequent reports with a user identity. */
    fun setUserId(userId: String?)
}

/**
 * `Firebase.crashlytics` throws when no `FirebaseApp` has been configured — a missing or
 * not-yet-added `google-services.json`/`GoogleService-Info.plist` on Android/iOS, which
 * this repo's own config files currently are. Resolution is lazy (not a constructor
 * default) and every call is caught via [runCatchingCancellable] (not `runCatching` —
 * that would also swallow a coroutine's cancellation), so a misconfigured Firebase
 * project degrades this one destination — via [onDiagnostic] — instead of crashing app
 * launch, matching the "a vendor SDK can never crash the host" guarantee the rest of the
 * observability pipeline holds.
 *
 * [provider] exists only so a test can inject a throwing lookup without a real Firebase
 * project — production always uses the default.
 */
internal class RealFirebaseCrashlyticsGateway(
    private val onDiagnostic: (String) -> Unit = {},
    private val provider: () -> FirebaseCrashlytics = { Firebase.crashlytics },
) : FirebaseCrashlyticsGateway {

    private val crashlytics: FirebaseCrashlytics? by lazy {
        runCatchingCancellable(provider)
            .onFailure { onDiagnostic("crashlytics: unavailable — ${it::class.simpleName}") }
            .getOrNull()
    }

    override fun recordException(throwable: Throwable) {
        // No diagnostic here on a null instance — the lazy above already reported
        // "unavailable" once at first access; repeating it per call would spam the channel.
        runCatchingCancellable { crashlytics?.recordException(throwable) }
            .onFailure { onDiagnostic("crashlytics: recordException failed — ${it::class.simpleName}") }
    }

    override fun log(message: String) {
        runCatchingCancellable { crashlytics?.log(message) }
            .onFailure { onDiagnostic("crashlytics: log failed — ${it::class.simpleName}") }
    }

    override fun setCustomKey(key: String, value: String) {
        runCatchingCancellable { crashlytics?.setCustomKey(key, value) }
            .onFailure { onDiagnostic("crashlytics: setCustomKey failed — ${it::class.simpleName}") }
    }

    override fun setUserId(userId: String?) {
        runCatchingCancellable { crashlytics?.setUserId(userId ?: "") }
            .onFailure { onDiagnostic("crashlytics: setUserId failed — ${it::class.simpleName}") }
    }
}
