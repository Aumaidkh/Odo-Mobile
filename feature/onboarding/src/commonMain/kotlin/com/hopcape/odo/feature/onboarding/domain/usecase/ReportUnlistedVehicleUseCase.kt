package com.hopcape.odo.feature.onboarding.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.UnlistedVehicleReporter

/**
 * Tells Odo about a make/model the catalog didn't have, after the car step already saved the
 * car using it. See `:feature:garage`'s use case of the same name — each feature owns its own
 * copy rather than sharing one across features, same as [LoadVehicleCatalogUseCase].
 */
internal class ReportUnlistedVehicleUseCase(
    private val reporter: UnlistedVehicleReporter,
) {
    suspend operator fun invoke(make: String, model: String, variant: String?) =
        reporter.report(make, model, variant)
}
