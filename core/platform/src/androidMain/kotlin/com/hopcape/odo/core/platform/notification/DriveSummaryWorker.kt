package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.trip.analysis.DailyDriveSummary
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** Posts today's drive summary, if there were drives, after queueing tomorrow's run. */
internal class DriveSummaryWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val notifier: WorkManagerDriveSummaryNotifier by inject()
    private val activeCar: ActiveCarProvider by inject()
    private val cars: CarRepository by inject()
    private val trips: TripRepository by inject()
    private val prices: FuelPriceProvider by inject()
    private val city: CurrentCityProvider by inject()
    private val clock: Clock by inject()
    private val telemetry: DriveSummaryTelemetry by inject()

    override suspend fun doWork(): Result {
        notifier.rearmFromRun()
        val zone = TimeZone.currentSystemDefault()
        val now = clock.now().toLocalDateTime(zone)
        val day = now.date.toString()
        // Doze can hold the job; a summary at midnight reads as noise.
        if (now.hour >= LATEST_HOUR) return skip("late", day)
        val carId = withTimeoutOrNull(CAR_TIMEOUT_MILLIS) { activeCar.activeCarId.filterNotNull().first() }
            ?: return skip("no_car", day)

        val start = now.date.atStartOfDayIn(zone)
        val end = now.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.milliseconds
        val meters = trips.countedBetween(carId, start, end).map { it.distance.meters }
        val fuel = cars.observe(carId).first()?.fuelType
        val rate = fuel?.let { prices.priceFor(city.currentCity(), it) }?.let { FuelEfficiencyPolicy.ratePerKm(it).paise }
        val summary = DailyDriveSummary.of(meters, rate) ?: return skip("no_drives", day)

        if (DriveSummaryNotification.show(applicationContext, summary)) telemetry.posted(summary.drives, summary.km, day)
        return Result.success()
    }

    private fun skip(reason: String, day: String): Result {
        telemetry.skipped(reason, day)
        return Result.success()
    }

    private companion object {
        const val LATEST_HOUR = 23
        const val CAR_TIMEOUT_MILLIS = 5_000L
    }
}
