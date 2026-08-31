package com.hopcape.odo.feature.profile.domain.usecase

import com.hopcape.odo.core.domain.city.UnlistedCityReporter

/**
 * Tells Odo about a city the catalog didn't have, after a profile using it was already saved.
 * Thin: the only reason this exists rather than calling [UnlistedCityReporter] straight from
 * the ViewModel is the same reason every other use case here does — the ViewModel talks to the
 * feature's own domain layer, never to a `:core:domain` port directly.
 */
internal class ReportUnlistedCityUseCase(
    private val reporter: UnlistedCityReporter,
) {
    suspend operator fun invoke(name: String) = reporter.report(name)
}
