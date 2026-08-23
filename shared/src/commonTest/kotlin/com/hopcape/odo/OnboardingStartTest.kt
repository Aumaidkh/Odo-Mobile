package com.hopcape.odo

import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStartTest {

    private fun config(video: Boolean = false) = object : OnboardingConfig {
        override val videoEnabled = video
    }

    @Test
    fun aReturningOwnerOpensAtHome() {
        assertEquals(OdoDestination.Home, onboardingStartDestination(returning = true, config()))
    }

    @Test
    fun aNewInstallOpensAtWelcome() {
        assertEquals(OdoDestination.Welcome, onboardingStartDestination(returning = false, config()))
    }

    @Test
    fun theVideoFlagDoesNotChangeWhereANewInstallOpens_becauseTheFlowIsNotBuilt() {
        // A remote flag can only reach code the APK already contains, and there is no video
        // flow yet. This asserts the no-op deliberately: when the flow ships, this test is
        // what fails and says the branch still needs pointing at it.
        assertEquals(
            OdoDestination.Welcome,
            onboardingStartDestination(returning = false, config(video = true)),
        )
    }

    @Test
    fun theVideoFlagNeverOverridesAReturningOwner() {
        // Onboarding of any kind is for a first install. A returning owner goes Home
        // whatever the variant says.
        assertEquals(
            OdoDestination.Home,
            onboardingStartDestination(returning = true, config(video = true)),
        )
    }
}
