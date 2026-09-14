package com.hopcape.odo.feature.garage.domain

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.feature.garage.domain.model.OdometerCorrection
import com.hopcape.odo.feature.garage.domain.model.RestoredHistory

/**
 * Whether a restored car is worth telling the owner about, and what to say.
 *
 * Pure, so the two rules that matter are testable without a database: an empty restore is
 * not announced, and the odometer correction may only ever raise the reading.
 */
internal object RestoredHistoryPolicy {

    fun summarise(
        car: Car,
        logs: List<ServiceLogEntry>,
        documents: List<Document>,
        fills: List<FuelFill>,
    ): RestoredHistory? {
        if (logs.isEmpty() && documents.isEmpty() && fills.isEmpty()) return null

        return RestoredHistory(
            carId = car.id,
            carName = car.modelName,
            registrationNumber = car.registrationNumber?.value,
            serviceLogCount = logs.size,
            documentCount = documents.size,
            fuelFillCount = fills.size,
            since = logs.minOfOrNull { it.serviceDate },
            odometer = correction(car, logs, fills),
        )
    }

    /**
     * The highest reading anywhere in the restored history, when it beats what the car
     * carries.
     *
     * Equal is not a correction — there is nothing to put right, and offering it would make
     * the owner answer a question about nothing. Lower is refused outright: the reading the
     * owner typed on this phone may be a guess, but an odometer that counts down is worse
     * than a guess.
     */
    private fun correction(car: Car, logs: List<ServiceLogEntry>, fills: List<FuelFill>): OdometerCorrection? {
        val history = maxOf(
            logs.maxOfOrNull { it.odometer.km } ?: 0,
            fills.mapNotNull { it.odometer?.km }.maxOrNull() ?: 0,
        )
        if (history <= car.odometer.km) return null
        return OdometerCorrection(currentKm = car.odometer.km, historyKm = history)
    }
}
