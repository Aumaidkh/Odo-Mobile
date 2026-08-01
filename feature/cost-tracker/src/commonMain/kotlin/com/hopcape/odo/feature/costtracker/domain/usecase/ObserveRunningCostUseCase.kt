package com.hopcape.odo.feature.costtracker.domain.usecase

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod
import com.hopcape.odo.feature.costtracker.domain.model.RunningCostSnapshot
import com.hopcape.odo.feature.costtracker.domain.model.SpendBucket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * What a car costs to run over the chosen period, as a stream.
 *
 * Gathers the four things the math needs — the car (for its fuel type), its service logs,
 * its odometer readings and a fuel price — and hands them to
 * [RunningCostCalculator], which owns the arithmetic. This use case only decides *which
 * windows* to ask about: the period itself, the window before it for the trend, and one
 * per bar of the chart.
 *
 * The readings are observed rather than read once, because the car's own reading is
 * changed from the garage: without that, the ₹/km on screen would stay stale until the
 * next service log.
 *
 * A car with no city set, or one in a city Odo carries no prices for, simply gets no fuel
 * rate — the cost then covers maintenance only, and the screen says so rather than
 * presenting a partial figure as the whole.
 */
internal class ObserveRunningCostUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val city: CurrentCityProvider,
    private val fuelPrices: FuelPriceProvider,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId, period: CostPeriod): Flow<RunningCostSnapshot> = combine(
        cars.observe(carId),
        logs.observe(carId),
        logs.observeOdometerReadings(carId),
    ) { car, entries, readings ->
        snapshotOf(car, entries, readings, period)
    }

    private suspend fun snapshotOf(
        car: Car?,
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        period: CostPeriod,
    ): RunningCostSnapshot {
        // Resolved once per emission, so the period, its predecessor and every bar of the
        // chart are measured against the same day.
        val today = clock.now().toLocalDateTime(timeZone).date
        val fuelPrice = car?.let { fuelPrices.priceFor(city.currentCity(), it.fuelType) }
        val fuelRate = fuelPrice?.let(FuelEfficiencyPolicy::ratePerKm)
        val window = CostWindow.endingOn(today, period.months)

        return RunningCostSnapshot(
            car = car,
            period = period,
            cost = costOver(window, entries, readings, fuelRate),
            previous = costOver(window.previous(), entries, readings, fuelRate),
            buckets = bucketsFor(period, today, entries, readings, fuelRate),
            fuelPrice = fuelPrice,
            today = today,
        )
    }

    private fun costOver(
        window: CostWindow,
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        fuelRate: Amount?,
    ) = RunningCostCalculator.compute(
        window = window,
        entries = entries,
        readings = readings,
        fuelRatePerKm = fuelRate,
    )

    /**
     * The chart's bars, oldest first. Each is its own window run through the same math, so
     * a bar includes its slice of the fuel estimate and the bars add up to the period.
     */
    private fun bucketsFor(
        period: CostPeriod,
        today: LocalDate,
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        fuelRate: Amount?,
    ): List<SpendBucket> = (0 until period.buckets).map { index ->
        val monthsBack = (period.buckets - 1 - index) * period.monthsPerBucket
        val window = CostWindow.endingOn(
            day = today.minus(monthsBack, DateTimeUnit.MONTH),
            months = period.monthsPerBucket,
        )
        SpendBucket(
            window = window,
            spend = costOver(window, entries, readings, fuelRate).totalSpend,
        )
    }
}
