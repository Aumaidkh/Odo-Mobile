package com.hopcape.odo.core.domain.owner

/**
 * The user's city — the key for fairness benchmarks ("Pune average"). `null` until a
 * profile/location flow provides one; with no city there is no fairness verdict.
 */
fun interface CurrentCityProvider {
    fun currentCity(): String?
}
