package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [ActiveCarProvider] over the owner's **primary** car — the MVP's answer to "which car?",
 * where a garage holds exactly one.
 *
 * Multi-car (Phase 2) replaces this with a stored selection; every caller reads the port, so
 * that is one class and one DI line.
 *
 * Started [SharingStarted.Eagerly] and kept for the process: the id has to be there the moment
 * a tap asks for it, and a navigation handler has nowhere to await a first emission. A failed
 * read collapses to `null` rather than propagating — the callers' `null` branch (do nothing)
 * is already the right behaviour for "we can't name a car", and a throw here would take down
 * whichever tap happened to be reading.
 */
internal class PrimaryCarProvider(
    cars: CarRepository,
    telemetry: DataTelemetry,
    // The provider outlives every screen, so it owns its scope rather than borrowing one. A
    // SupervisorJob keeps a failure in this flow from cancelling anything else launched on it.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ActiveCarProvider {

    override val activeCarId: StateFlow<CarId?> = cars.observePrimaryCar()
        .map { car -> car?.id }
        .catch { cause ->
            telemetry.crashed(DataTelemetry.CAR, OPERATION, cause)
            emit(null)
        }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    private companion object {
        const val OPERATION = "observeActive"
    }
}
