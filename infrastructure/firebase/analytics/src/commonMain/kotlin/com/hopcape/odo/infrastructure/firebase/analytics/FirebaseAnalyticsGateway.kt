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

internal class RealFirebaseAnalyticsGateway(
    private val analytics: FirebaseAnalytics = Firebase.analytics,
) : FirebaseAnalyticsGateway {

    override fun logEvent(name: String, parameters: Map<String, Any>) =
        analytics.logEvent(name, parameters)

    override fun setUserId(id: String?) = analytics.setUserId(id)

    override fun setUserProperty(name: String, value: String) = analytics.setUserProperty(name, value)
}
