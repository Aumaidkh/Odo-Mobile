package com.hopcape.odo.infrastructure.firebase.analytics

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.analytics.analytics

// ─────────────────────────────────────────────────────────────
// FirebaseAnalyticsGateway — the real Firebase SDK surface this module
// calls, narrowed to what FirebaseAnalyticsSink needs. The seam exists
// because gitlive's FirebaseAnalytics is a concrete SDK class (an expect
// class), not an interface, so nothing about it can be faked directly
// in a test — this is the fake-able boundary instead.
// ─────────────────────────────────────────────────────────────
internal interface FirebaseAnalyticsGateway {
    fun logEvent(name: String, parameters: Map<String, Any>)
    fun setUserId(id: String?)
    fun setUserProperty(name: String, value: String)
}

/**
 * `Firebase.analytics` throws when no `FirebaseApp` has been configured — a missing or
 * not-yet-added `google-services.json`/`GoogleService-Info.plist` on Android/iOS, which
 * this repo's own config files currently are. Resolution is lazy (not a constructor
 * default) and every call is caught, so a misconfigured Firebase project degrades this
 * one destination — via [onDiagnostic] — instead of crashing app launch, matching the
 * "a vendor SDK can never crash the host" guarantee the rest of the pipeline holds.
 *
 * [provider] exists only so a test can inject a throwing lookup without a real Firebase
 * project — production always uses the default.
 */
internal class RealFirebaseAnalyticsGateway(
    private val onDiagnostic: (String) -> Unit = {},
    private val provider: () -> FirebaseAnalytics = { Firebase.analytics },
) : FirebaseAnalyticsGateway {

    private val analytics: FirebaseAnalytics? by lazy {
        runCatching(provider)
            .onFailure { onDiagnostic("firebase: analytics unavailable — ${it::class.simpleName}") }
            .getOrNull()
    }

    override fun logEvent(name: String, parameters: Map<String, Any>) {
        runCatching { analytics?.logEvent(name, parameters) }
            .onFailure { onDiagnostic("firebase: logEvent failed — ${it::class.simpleName}") }
    }

    override fun setUserId(id: String?) {
        runCatching { analytics?.setUserId(id) }
            .onFailure { onDiagnostic("firebase: setUserId failed — ${it::class.simpleName}") }
    }

    override fun setUserProperty(name: String, value: String) {
        runCatching { analytics?.setUserProperty(name, value) }
            .onFailure { onDiagnostic("firebase: setUserProperty failed — ${it::class.simpleName}") }
    }
}
