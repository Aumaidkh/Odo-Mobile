package com.hopcape.odo.feature.onboarding

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoTelemetry
import com.hopcape.performance.api.APM
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Drives every analytics call of the onboarding facades and checks each one is declared. */
class OnboardingAnalyticsEventsTest {

    @Test
    fun everyEventTheFacadesSendIsDeclared() = runTest {
        val analytics = Recording()
        val ids = IdGenerator { "id" }
        OnboardingTelemetry(HLogger.asLogger(), analytics, APM.asTracer(), ids).apply {
            welcomeShown()
            welcomeShown(variant = OnboardingTelemetry.Variant.FRESH)
            welcomeCompleted()
            welcomeCompleted(makeSource = OnboardingTelemetry.MakeSource.TILE, make = "Hyundai")
            resumed(step = "CAR")
            resumeReset()
            makeOtherOpened()
            makeOtherDismissed()
            makesLoad<Unit> { error("offline") }
            signInFromWelcome()
            legalOpened(document = "terms")
        }
        WelcomeVideoTelemetry(HLogger.asLogger(), analytics, ids).apply {
            shown(pages = 3)
            pageViewed(page = 1)
            skipped(page = 1)
            completed()
            clipFailed(page = 2)
        }

        val problems = analytics.events.flatMap { (name, props) -> onboardingAnalyticsEvents.problemsWith(name, props) }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private class Recording : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }
        override fun identify(traits: UserTraits) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
