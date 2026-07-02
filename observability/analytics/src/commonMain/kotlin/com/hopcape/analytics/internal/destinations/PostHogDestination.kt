package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.model.AnalyticsEvent

// ─────────────────────────────────────────────────────────────
// PostHogDestination — Odo's primary analytics vendor (North Star:
// bills scanned/month). Thin adapter over the PostHog SDK; the
// real calls are sketched in comments to keep this focused on
// architecture rather than SDK plumbing (see the module README's
// "Known limitations" — this is a stub until the SDK is wired).
// ─────────────────────────────────────────────────────────────
internal class PostHogDestination : AnalyticsDestination {

    override val name: String = "posthog"

    override fun identify(traits: UserTraits) {
        // PostHog.identify(traits.userId, userProperties = traits.traits)
    }

    override fun track(event: AnalyticsEvent) {
        // PostHog.capture(event = event.name, properties = event.properties)
    }

    override fun flush() {
        // PostHog.flush()
    }
}
