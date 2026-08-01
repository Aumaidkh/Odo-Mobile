package com.hopcape.odo.feature.costtracker.presentation.runningcost

import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod

/**
 * What the owner did on the running-cost screen, as data.
 *
 * The screen only reads — the one thing it can change is which window it is looking at —
 * so there is nothing here that leaves it, and no effects to go with it.
 */
internal sealed interface RunningCostEvent {

    /** A period chip was tapped. */
    data class PeriodSelected(val period: CostPeriod) : RunningCostEvent
}
