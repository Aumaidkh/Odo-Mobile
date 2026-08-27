package com.hopcape.odo

import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStartTest {

    private fun config(video: Boolean = false) = object : OnboardingConfig {
        override val videoEnabled = video
        override val refuelVideoUrl = ""
        override val scannerVideoUrl = ""
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
    fun theVideoFlagSendsANewInstallToTheVideoIntro() {
        assertEquals(
            OdoDestination.WelcomeVideo,
            onboardingStartDestination(returning = false, config(video = true)),
        )
    }

    @Test
    fun theVideoIntroIsChosenEvenWithNoClipsConfigured() {
        // Blank URLs are the ordinary case until the clips are published. The screen shows
        // its copy without the video; routing does not second-guess that, because "no clip"
        // is a rendering state and sending the owner to a different intro would change what
        // the flag means.
        assertEquals(
            OdoDestination.WelcomeVideo,
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
