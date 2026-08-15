package com.hopcape.odo.feature.refuel.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.analysis.OdometerPrediction
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Fills in everything about a fill that Odo can work out for itself.
 *
 * This is what makes the whole feature cheap for the owner. Typing a fill from scratch is
 * four fields; starting from the last visit's station, today's rate and a predicted odometer
 * leaves one number to type, and often none at all. The same builder serves every channel —
 * a detected payment and a pump photo both arrive with some fields already known, and this
 * completes the rest around them.
 *
 * Nothing here is treated as fact. Every field it supplies is marked with where it came from
 * ([FieldOrigin.HISTORY], [FieldOrigin.PREDICTED]) so the confirm step can show which numbers
 * are Odo's guesses and which the owner or a machine actually observed.
 *
 * A missing input is never an error. No car, no history, no known price: each of those just
 * leaves its field blank, and the confirm step asks for it.
 */
internal class BuildFillDraftUseCase(
    private val cars: CarRepository,
    private val fills: FuelFillRepository,
    private val logs: ServiceLogRepository,
    private val fuelPrices: FuelPriceProvider,
    private val city: CurrentCityProvider,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /**
     * Complete [partial] with whatever the car's history and today's prices can supply.
     *
     * Fields already set on [partial] are never overwritten — a channel that observed a
     * number knows better than this does. [predictOdometer] is the owner's setting: with it
     * off, the drum opens on their last known reading rather than a projection, which is the
     * behaviour of someone who would rather type than check a guess.
     */
    suspend operator fun invoke(
        carId: CarId,
        partial: FuelFillDraft,
        predictOdometer: Boolean = true,
    ): FuelFillDraft {
        val car = cars.observe(carId).first()
        val unit = car?.fuelType?.let(FuelUnit::of) ?: partial.unit
        val lastFill = fills.latestForCar(carId).getOrNull()

        val rate = partial.pricePerUnit ?: ownerRate(carId)
        // Only read the car's readings when the channel did not supply one. A pump photo
        // that already carries an odometer should not pay for a query it will discard.
        val odometer = if (partial.odometerKm != null) null else predictOdometer(carId, predictOdometer)

        return partial.copy(
            unit = unit,
            pricePerUnit = rate,
            priceOrigin = when {
                partial.pricePerUnit != null -> partial.priceOrigin
                rate != null -> FieldOrigin.HISTORY
                else -> FieldOrigin.UNKNOWN
            },
            // Without a rate there is nothing to divide the amount by, so the quantity has to
            // stay empty too. `completed()` would leave it empty anyway; clearing it here is
            // what stops a quantity derived from an *earlier* rate surviving the rebuild.
            quantityMilli = if (rate == null && partial.quantityMilli == null) null else partial.quantityMilli,
            odometerKm = partial.odometerKm ?: odometer?.km,
            odometerOrigin = when {
                partial.odometerKm != null -> partial.odometerOrigin
                odometer == null -> FieldOrigin.UNKNOWN
                odometer.predicted -> FieldOrigin.PREDICTED
                else -> FieldOrigin.HISTORY
            },
            stationName = partial.stationName ?: lastFill?.stationName,
        ).completed()
    }

    /** A blank draft for a channel that starts with nothing but the owner's history. */
    suspend fun prefilled(carId: CarId, predictOdometer: Boolean = true): FuelFillDraft =
        invoke(
            carId = carId,
            partial = FuelFillDraft(source = FillEntrySource.PREFILLED),
            predictOdometer = predictOdometer,
        )

    /**
     * The rate the owner set for themselves, and only that one.
     *
     * Deliberately not the seeded city table, and not what they last paid. A quantity is the
     * one number on the confirm step nobody observed — it is the amount divided by this rate —
     * and dividing by a figure that shipped with the app produces a confident-looking litre
     * count the owner never agreed to. Better to leave it blank and ask for the rate: that is
     * one trip to a screen they own, once, against a wrong figure on every fill.
     *
     * `null` here is what puts the "fuel price isn't set" prompt on the confirm step.
     */
    private suspend fun ownerRate(carId: CarId): Amount? {
        val car = cars.observe(carId).first() ?: return null
        val price = fuelPrices.priceFor(city.currentCity(), car.fuelType) ?: return null
        return price.pricePerUnit.takeIf { price.source == FuelPriceSource.OWNER }
    }

    /**
     * Where the odometer probably reads now, from every reading the car has.
     *
     * Fills carry readings too, and they are the most recent ones for an owner who logs fuel
     * more often than service. Leaving them out would anchor the projection on a service
     * from months ago and overshoot every time.
     */
    private suspend fun predictOdometer(carId: CarId, predict: Boolean): PredictedOdometer? {
        val readings = logs.observeOdometerReadings(carId).first()
        val today = clock.now().toLocalDateTime(timeZone).date
        val prediction = OdometerPrediction.forToday(readings, today) ?: return null
        return if (predict) {
            PredictedOdometer(prediction.km, prediction.predicted)
        } else {
            // The owner turned prediction off, so only a real reading is offered. Rerunning
            // the projection and discarding it would still be a projection.
            PredictedOdometer(
                km = readings.maxOfOrNull { it.odometer.km } ?: prediction.km,
                predicted = false,
            )
        }
    }

    private data class PredictedOdometer(val km: Int, val predicted: Boolean)
}
