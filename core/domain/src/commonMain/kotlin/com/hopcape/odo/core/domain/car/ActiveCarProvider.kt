package com.hopcape.odo.core.domain.car

import com.hopcape.odo.core.domain.car.model.CarId
import kotlinx.coroutines.flow.StateFlow

/**
 * Port resolving which car the app is currently acting for.
 *
 * Every per-car surface — the service log, the timeline, the dashboard's shortcuts — has to
 * name a car in its navigation key, and until this existed each of them answered the question
 * with its own hardcoded id. That is the failure this port closes: five features guessing
 * separately is five ways for the app to open a car that isn't the owner's.
 *
 * A [StateFlow] rather than a suspending read, because the callers are navigation handlers: a
 * tap has to resolve a car *now*, and a `null` (setup hasn't stored a car yet) is a reason to
 * do nothing rather than to wait. Anything that wants to react to the car changing collects it.
 *
 * MVP has one car, so today's adapter answers with the owner's primary car. Multi-car (Phase 2)
 * makes "active" a real choice — a stored selection rather than a derivation — and that is a
 * change to the implementation, not to this contract or to any caller.
 */
interface ActiveCarProvider {

    /** The active car's id, or `null` before setup has stored one. */
    val activeCarId: StateFlow<CarId?>
}
