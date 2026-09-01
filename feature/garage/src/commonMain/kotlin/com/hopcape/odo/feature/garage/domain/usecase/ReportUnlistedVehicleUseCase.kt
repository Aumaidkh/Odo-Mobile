package com.hopcape.odo.feature.garage.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.UnlistedVehicleReporter

/**
 * Tells Odo about a make/model the catalog didn't have, after a car using it was already
 * saved. Thin: the only reason this exists rather than calling [UnlistedVehicleReporter]
 * straight from the ViewModel is the same reason every other use case here does — the
 * ViewModel talks to the feature's own domain layer, never to a `:core:domain` port directly.
 */
internal class ReportUnlistedVehicleUseCase(
    private val reporter: UnlistedVehicleReporter,
) {
    suspend operator fun invoke(make: String, model: String, variant: String?) =
        reporter.report(make, model, variant)
}
