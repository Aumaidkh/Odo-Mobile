package com.hopcape.odo.core.domain.settings.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyPreferencesTest {

    @Test
    fun defaults_areTheOnesTheOnboardingCardAnnounces() {
        // These three are stated to the owner on the first-run consent card, and the card's
        // copy is written against them. A default changed here without changing that copy
        // makes the app say one thing and do another.
        val defaults = PrivacyPreferences.Default

        assertFalse(defaults.keepTripRoutes, "routes are the most revealing thing stored")
        assertTrue(defaults.usageAnalytics)
    }

    @Test
    fun defaults_areWhatAnUntouchedAppSettingsCarries() {
        assertEquals(PrivacyPreferences.Default, AppSettings.Default.privacy)
    }
}
