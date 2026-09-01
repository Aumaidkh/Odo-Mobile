package com.hopcape.odo

import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

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

    /**
     * A remote backend the fetch has not reached yet: the flag reads false until
     * [refresh] completes, which takes [fetchTakes] of (virtual) time.
     */
    private class SlowRemote(
        private val fetchTakes: kotlin.time.Duration,
    ) : OnboardingConfig, ConfigRefresher {
        var fetched = false
            private set
        var refreshCalls = 0
            private set

        override val videoEnabled: Boolean get() = fetched
        override val refuelVideoUrl = ""
        override val scannerVideoUrl = ""

        override suspend fun refresh() {
            refreshCalls += 1
            delay(fetchTakes)
            fetched = true
        }
    }

    @Test
    fun aNewInstallWaitsForTheFirstFetchBeforeChoosing() = runTest {
        // Issue #351: on a fresh install the first fetch has not landed when the start
        // destination is decided, so the flag reads false and the old onboarding shows —
        // the video variant only appears on the next launch. The decision must wait for
        // the fetch.
        val remote = SlowRemote(fetchTakes = 1.seconds)
        assertEquals(
            OdoDestination.WelcomeVideo,
            onboardingStartDestination(returning = false, config = remote, refresher = remote),
        )
    }

    @Test
    fun theWaitIsBoundedSoAnOfflineInstallStillOpens() = runTest {
        // A device that cannot reach the backend must not sit on the startup screen.
        // The wait has a ceiling, after which the compiled default decides.
        val neverCompletes = object : ConfigRefresher {
            override suspend fun refresh() = awaitCancellation()
        }
        assertEquals(
            OdoDestination.Welcome,
            onboardingStartDestination(returning = false, config(), neverCompletes),
        )
    }

    @Test
    fun aReturningOwnerNeverWaitsOnTheFetch() = runTest {
        // Home is the answer whatever the config says, so there is nothing to wait for.
        val remote = SlowRemote(fetchTakes = 1.seconds)
        assertEquals(
            OdoDestination.Home,
            onboardingStartDestination(returning = true, config = remote, refresher = remote),
        )
        assertEquals(0, remote.refreshCalls)
    }
}
