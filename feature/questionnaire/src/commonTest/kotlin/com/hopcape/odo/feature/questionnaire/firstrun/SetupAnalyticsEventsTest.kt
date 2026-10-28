package com.hopcape.odo.feature.questionnaire.firstrun

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.SetupTelemetry
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.CatalogOptions
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.performance.api.APM
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Drives every analytics call of the setup facade and checks each one is declared. */
class SetupAnalyticsEventsTest {

    private val car: Car = Car.create(
        id = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        make = "Hyundai",
        model = "Creta",
        year = 2022,
        fuelType = FuelType.PETROL,
        odometerKm = null,
        odometerPending = true,
        isPrimary = true,
    ).getOrElse { error("invalid fixture: $it") }

    @Test
    fun everyEventTheFacadeSendsIsDeclared() = runTest {
        val analytics = Recording()
        SetupTelemetry(HLogger.asLogger(), analytics, APM.asTracer(), IdGenerator { "id" }).apply {
            started()
            stepAdvanced(OnboardingStep.CAR)
            stepBack(OnboardingStep.CAR)
            stepShown("model")
            modelChosen(source = "tile")
            modelSearchOpened()
            modelSearchDismissed()
            changeMakeClicked()
            olderYearsOpened()
            yearChosen(year = 2022, source = "tile", changed = false)
            fuelChosen(fuel = "PETROL", changed = true)
            catalogLoadFailed()
            catalogEmpty()
            abandoned("model", listOf("model" to false), catalog = "ready")
            fieldAnswered("model", "model")
            catalogRetried()
            goalSelected(OnboardingGoal.TRACK_COSTS)
            workshopTierSelected("local")
            lastServiceForgotten(forgot = true)
            odometerSkipped()
            lastServiceSkipped()
            lastServiceRefused(field = "date")
            completed()
            catalogLoad { null }
            catalogLoad { CatalogOptions(emptyList(), emptyList(), 2000..2026, emptyList()) }
            modelsLoad("Hyundai") { emptyList() }
            carSave(edit = false, make = null, fuel = null) { car.right() }
            carSave(edit = true, make = "Hyundai", fuel = "PETROL") { car.right() }
            carSave(edit = false, make = null, fuel = null) { nonEmptyListOf<DomainError>(DomainError.BlankMake).left() }
        }

        val problems = analytics.events.flatMap { (name, props) -> setupAnalyticsEvents.problemsWith(name, props) }
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
