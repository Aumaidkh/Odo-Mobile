package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.model.AnalyticsEvent

// ─────────────────────────────────────────────────────────────
// FirebaseDestination — secondary destination (Firebase Analytics
// alongside FCM). Thin adapter over the Firebase SDK; real calls
// sketched in comments. Stub until the SDK is wired (see README).
// ─────────────────────────────────────────────────────────────
internal class FirebaseDestination : AnalyticsDestination {

    override val name: String = "firebase"

    override fun identify(traits: UserTraits) {
        // FirebaseAnalytics.getInstance(context).setUserId(traits.userId)
        // traits.traits.forEach { (k, v) -> setUserProperty(k, v.toString()) }
    }

    override fun track(event: AnalyticsEvent) {
        // FirebaseAnalytics.getInstance(context).logEvent(event.name, bundleOf(event.properties))
    }

    override fun flush() {
        // Firebase batches internally; an explicit flush is rarely needed.
    }
}
