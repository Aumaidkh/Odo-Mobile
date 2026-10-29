package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.trip.analysis.WeeklySummary
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/** Posts the Sunday recap of the past 7 days, after queueing next Sunday's run. */
internal class WeeklySummaryWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val notifier: WorkManagerDriveSummaryNotifier by inject()
    private val activeCar: ActiveCarProvider by inject()
    private val cars: CarRepository by inject()
    private val trips: TripRepository by inject()
    private val serviceLogs: ServiceLogRepository by inject()
    private val fills: FuelFillRepository by inject()
    private val prices: FuelPriceProvider by inject()
    private val city: CurrentCityProvider by inject()
    private val clock: Clock by inject()
    private val telemetry: DriveSummaryTelemetry by inject()

    override suspend fun doWork(): Result {
        notifier.rearmWeeklyFromRun()
        val zone = TimeZone.currentSystemDefault()
        val today = clock.now().toLocalDateTime(zone).date
        val weekStart = today.minus(6, DateTimeUnit.DAY)
        val day = today.toString()
        val carId = withTimeoutOrNull(CAR_TIMEOUT_MILLIS) { activeCar.activeCarId.filterNotNull().first() }
            ?: return skip("no_car", day)

        val meters = trips.countedBetween(
            carId,
            weekStart.atStartOfDayIn(zone),
            today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        ).map { it.distance.meters }
        val fuel = cars.observe(carId).first()?.fuelType
        val rate = fuel?.let { prices.priceFor(city.currentCity(), it) }?.let { FuelEfficiencyPolicy.ratePerKm(it) }
        val cost = RunningCostCalculator.compute(
            window = CostWindow(weekStart, today),
            entries = serviceLogs.observe(carId).first(),
            readings = serviceLogs.observeOdometerReadings(carId).first(),
            fuelRatePerKm = rate,
        )
        val mileage = WeeklySummary.mileageFor(fills.observeForCar(carId).first(), weekStart, today)
        val summary = WeeklySummary.of(meters, cost.perKm?.paise, mileage) ?: return skip("no_data", day)

        if (WeeklySummaryNotification.show(applicationContext, summary)) telemetry.weeklyPosted(summary.partNames(), day)
        return Result.success()
    }

    private fun skip(reason: String, day: String): Result {
        telemetry.weeklySkipped(reason, day)
        return Result.success()
    }

    private fun WeeklySummary.partNames(): String =
        listOfNotNull(km?.let { "km" }, perKmPaise?.let { "per_km" }, distancePerUnit?.let { "mileage" }).joinToString(",")

    private companion object {
        const val CAR_TIMEOUT_MILLIS = 5_000L
    }
}
