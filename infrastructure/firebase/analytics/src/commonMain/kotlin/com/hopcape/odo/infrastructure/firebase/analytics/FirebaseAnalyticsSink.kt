package com.hopcape.odo.infrastructure.firebase.analytics

import com.hopcape.analytics.api.AnalyticsSink
import com.hopcape.analytics.api.UserTraits

// ─────────────────────────────────────────────────────────────
// FirebaseAnalyticsSink — the AnalyticsSink adapter registered as an
// extra destination alongside PostHog (see AnalyticsConfig.destinations).
// Every call is sanitized against Firebase's own naming/type/length
// constraints first, so a malformed event is dropped and reported
// through onDiagnostic rather than silently rejected deep inside the
// vendor SDK.
// ─────────────────────────────────────────────────────────────
internal class FirebaseAnalyticsSink(
    private val gateway: FirebaseAnalyticsGateway = RealFirebaseAnalyticsGateway(),
    private val onDiagnostic: (String) -> Unit = {},
    private val sanitizer: FirebaseEventSanitizer = FirebaseEventSanitizer(onDiagnostic),
) : AnalyticsSink {

    override val name: String = "firebase"

    override fun identify(traits: UserTraits) {
        gateway.setUserId(traits.userId)
        traits.traits.forEach { (key, value) ->
            val sanitizedKey = sanitizer.sanitizeUserPropertyName(key) ?: return@forEach
            if (value == null) {
                onDiagnostic("firebase: dropped user property '$key' — null value")
                return@forEach
            }
            gateway.setUserProperty(sanitizedKey, sanitizer.sanitizeUserPropertyValue(value.toString()))
        }
    }

    /**
     * [timestampMs] is not forwarded. Firebase Analytics has no client-side way to
     * backdate an event, so a retried or delayed delivery is stamped at the moment
     * it actually reaches the SDK, not when it originally happened.
     */
    override fun track(eventName: String, properties: Map<String, Any?>, timestampMs: Long) {
        val sanitized = sanitizer.sanitizeEvent(eventName, properties) ?: return
        gateway.logEvent(sanitized.name, sanitized.parameters)
    }

    override fun flush() {
        // The gitlive wrapper exposes no explicit flush; Firebase batches and
        // uploads internally.
    }
}
