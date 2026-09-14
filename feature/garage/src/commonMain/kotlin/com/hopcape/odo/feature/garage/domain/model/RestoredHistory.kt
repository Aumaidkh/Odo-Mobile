package com.hopcape.odo.feature.garage.domain.model

import com.hopcape.odo.core.domain.car.model.CarId
import kotlinx.datetime.LocalDate

/**
 * What signing in put back onto a car, as the owner needs it explained (issue #425).
 *
 * The counts are what came back, not what changed — the sheet is a welcome, not a diff.
 */
internal data class RestoredHistory(
    val carId: CarId,
    val carName: String,
    val registrationNumber: String?,
    val serviceLogCount: Int,
    val documentCount: Int,
    val fuelFillCount: Int,
    val since: LocalDate?,
    val odometer: OdometerCorrection?,
)

/**
 * The reading the car carries against the higher one its history proves.
 *
 * Only ever exists when [historyKm] is above [currentKm]. An odometer that goes backwards
 * corrupts ₹/km, the health score and every km check built on it, so the correction the
 * sheet offers can only raise the number — never lower it, and never merely restate it.
 */
internal data class OdometerCorrection(val currentKm: Int, val historyKm: Int)
