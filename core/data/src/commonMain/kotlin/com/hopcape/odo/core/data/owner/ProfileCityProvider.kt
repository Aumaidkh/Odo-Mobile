package com.hopcape.odo.core.data.owner

import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.CurrentCityProvider

/**
 * The owner's city, read from their stored profile.
 *
 * Returns `null` — and therefore turns every fairness verdict off — until they set one on
 * their profile screen. That is the intended behaviour: a benchmark keyed on the wrong city
 * is worse than no benchmark, and the PRD's guardrail against false precision applies just
 * as much to the city as to the sample size.
 *
 * A failed read is also `null`, because being unable to decide the city must never break a
 * flow whose real job is saving the owner's service. But it is **reported**: an unset city
 * and an unreadable one look identical from the outside, and only one of them is a bug.
 */
internal class ProfileCityProvider(
    private val local: ProfileLocalDataSource,
    private val telemetry: DataTelemetry,
) : CurrentCityProvider {

    override suspend fun currentCity(): String? =
        try {
            local.currentCity()
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.PROFILE, OP_CITY, e)
            null
        }

    private companion object {
        const val OP_CITY = "currentCity"
    }
}
