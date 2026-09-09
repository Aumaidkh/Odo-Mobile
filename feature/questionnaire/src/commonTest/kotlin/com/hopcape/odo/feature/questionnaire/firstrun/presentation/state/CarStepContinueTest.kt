package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The car step's Continue rule.
 *
 * The odometer is the app's spine, but it is not always in the owner's head: they may not be
 * near the car, or may simply not remember. Demanding it on step 1 of 4, before Odo has shown
 * them anything, is a wall with no way around it.
 */
class CarStepContinueTest {

    @Test
    fun theCarStep_continues_withoutAnOdometerReading() {
        val state = answeredCarStep(odometer = FormField())

        assertTrue(
            state.canContinue,
            "Continue must be live without a reading. It is the only way off step 1 for an " +
                "owner who is not at their car.",
        )
    }

    @Test
    fun theCarStep_stillContinues_onceAReadingIsGiven() {
        // The reading stays the path we want; making it optional must not make it awkward.
        val state = answeredCarStep(odometer = FormField(45_000L))

        assertTrue(state.canContinue)
    }

    /** Every answer the car step asks for except the odometer, on the manual route. */
    private fun answeredCarStep(odometer: FormField<Long>) = OnboardingUiState(
        step = OnboardingStep.CAR,
        manualEntry = true,
        car = CarStepState(plate = FormField(PLATE)),
        details = CarDetailsState(
            make = FormField("Maruti Suzuki"),
            model = FormField(CarModel("Swift", "VXI")),
            year = FormField(2021),
            fuel = FormField(FuelType.PETROL),
        ),
        odometer = odometer,
    )

    private companion object {
        const val PLATE = "MH12AB1234"
    }
}
