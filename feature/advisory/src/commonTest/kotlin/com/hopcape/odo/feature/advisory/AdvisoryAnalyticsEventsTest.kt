package com.hopcape.odo.feature.advisory

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.feature.advisory.presentation.AdvisoryTelemetry
import com.hopcape.performance.api.APM
import kotlin.test.Test
import kotlin.test.assertTrue

/** Drives every analytics call of the advisory facade and checks each one is declared. */
class AdvisoryAnalyticsEventsTest {

    @Test
    fun everyEventTheFacadeSendsIsDeclared() {
        val analytics = Recording()
        AdvisoryTelemetry(HLogger.asLogger(), analytics, APM.asTracer(), IdGenerator { "id" }).apply {
            valueShown(hasRecord = false, firstRun = true)
            scanClicked(firstRun = true)
            firstRunFinished(AdvisoryTelemetry.Via.SCAN)
            firstRunSkipped(viaBack = true)
            autoOdoOffered()
            autoOdoCardSeen()
            whyRangeToggled(expanded = true)
            autoOdoOfferAccepted()
            shareClicked()
            checklistShown(due = 3, priced = 2, entry = "home")
            checklistSaveClicked()
        }

        val problems = analytics.events.flatMap { (name, props) -> advisoryAnalyticsEvents.problemsWith(name, props) }
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
