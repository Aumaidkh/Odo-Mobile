package com.hopcape.odo.core.domain.health.analysis

import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The modelled score a car carries before Odo has seen anything of it.
 *
 * The rule it has to keep: an assumption is worth half of what proof earns, and history is
 * only ever proof.
 */
class EstimatedHealthScoreTest {

    @Test
    fun anUnrecordedCarScoresFarAboveTheEmptyRecord() {
        val estimated = EstimatedHealthScore.forUnrecordedCar().total

        // The number Home used to hide behind a checklist was the calculator's verdict on an
        // empty record — single digits, which reads as a verdict on the car rather than on
        // an app that has been shown nothing.
        assertTrue(estimated > 30, "an unrecorded car must not look neglected: $estimated")
        assertTrue(estimated < 60, "nor may an assumption look like a well-kept record: $estimated")
    }

    @Test
    fun historyEarnsNothingWithoutProof() {
        val history = EstimatedHealthScore.forUnrecordedCar()
            .factors.single { it.kind == HealthFactorKind.HISTORY }

        // Verified bills and a consistent odometer are the only things that earn it, so
        // there is no honest way to assume any of it.
        assertEquals(0, history.earned)
    }

    @Test
    fun everyAssumedFactorIsWorthHalfOfWhatProofWouldEarn() {
        EstimatedHealthScore.forUnrecordedCar().factors
            .filterNot { it.kind == HealthFactorKind.HISTORY }
            .forEach { factor ->
                assertEquals(factor.kind.weight / 2, factor.earned, factor.kind.name)
            }
    }
}
