package com.hopcape.odo.feature.costtracker.presentation.runningcost

import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod

/** What the owner did on the running-cost screen, as data. */
internal sealed interface RunningCostEvent {

    /** A period chip was tapped. */
    data class PeriodSelected(val period: CostPeriod) : RunningCostEvent

    /** The fuel note's action — correcting the price the estimate is built on. */
    data object FuelRateTapped : RunningCostEvent

    /** The odometer coach mark was tapped away. Seen forever (#229). */
    data object OdometerShowcaseDismissed : RunningCostEvent

    /** The screen left composition while the coach mark was up — release the grant, not seen. */
    data object OdometerShowcaseLeft : RunningCostEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface RunningCostEffect {

    /** Open the sheet where the owner states what they pay for fuel. */
    data object OpenFuelRate : RunningCostEffect
}
