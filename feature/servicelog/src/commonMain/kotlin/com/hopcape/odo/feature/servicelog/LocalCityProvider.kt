package com.hopcape.odo.feature.servicelog

import com.hopcape.odo.core.domain.owner.CurrentCityProvider

/**
 * M1 stub for [CurrentCityProvider] — a placeholder city until the real profile/location
 * flow lands. The single swap point for wiring the user's actual city into fairness.
 */
internal class LocalCityProvider : CurrentCityProvider {
    override fun currentCity(): String = "Pune"
}
