package com.hopcape.odo.feature.garage.presentation

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.formatKm
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_field_odometer
import com.hopcape.odo.feature.garage.resources.gr_error_no_car
import com.hopcape.odo.feature.garage.resources.gr_error_odo_ahead
import com.hopcape.odo.feature.garage.resources.gr_error_odo_back
import com.hopcape.odo.feature.garage.resources.gr_error_save_failed

/**
 * What to tell the owner about a failure, per domain error.
 *
 * The odometer cases name the reading they conflict with, because "that reading is too
 * low" leaves the owner guessing what would be high enough. Everything else falls back to
 * a plain "couldn't save": an owner cannot act on a persistence failure, and the detail is
 * already on its way to the crash dashboard.
 */
internal fun DomainError.toOdometerMessage(): UiText = when (this) {
    is DomainError.OdometerRegression -> UiText(Res.string.gr_error_odo_back, listOf(previousKm.asKm()))
    is DomainError.OdometerAheadOfLaterEntry -> UiText(Res.string.gr_error_odo_ahead, listOf(nextKm.asKm()))
    DomainError.MissingOdometer, DomainError.NegativeOdometer -> UiText(Res.string.gr_error_field_odometer)
    DomainError.CarNotFound -> UiText(Res.string.gr_error_no_car)
    else -> UiText(Res.string.gr_error_save_failed)
}

/** Grouped the way every other reading in the app is written — "45,000 km", not "45000". */
private fun Int.asKm(): String = Distance.of(this).fold({ toString() }, { it.formatKm() })
