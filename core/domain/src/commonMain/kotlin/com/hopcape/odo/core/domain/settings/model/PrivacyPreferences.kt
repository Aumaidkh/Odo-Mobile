package com.hopcape.odo.core.domain.settings.model

/**
 * What the app is allowed to keep and to count, on this device.
 *
 * Device-scoped like the rest of [AppSettings]. The third privacy switch the owner sees —
 * "Share prices anonymously" — is deliberately **not** here: it decides what the server may
 * do with rows that belong to the account, so it lives on
 * [OwnerProfile][com.hopcape.odo.core.domain.owner.model.OwnerProfile] and travels with it.
 * Two of the three describe this phone; one describes the account.
 *
 * Defaults differ by switch on purpose:
 *
 * - [keepTripRoutes] is off. A route is the most revealing thing the app could hold, and
 *   the distance it exists to produce is measured during the trip rather than recomputed
 *   from stored points — so keeping them buys the owner nothing by default.
 * - [usageAnalytics] is on, and onboarding says so on a card the owner can turn off in one
 *   tap. Notice plus an easy withdrawal is what makes an opt-out default defensible.
 *
 * [usageAnalytics] governs product analytics and nothing else. Crash reporting and the
 * diagnostic log upload keep their own gates, so the switch's copy must not claim them.
 */
data class PrivacyPreferences(
    /** Keep the start/end coordinates of automatically-detected trips. Off = distance only. */
    val keepTripRoutes: Boolean = false,
    /** Count which screens and features get used — [com.hopcape.analytics] consent. */
    val usageAnalytics: Boolean = true,
) {
    companion object {
        /** What a device has before anything is chosen. */
        val Default: PrivacyPreferences = PrivacyPreferences()
    }
}
