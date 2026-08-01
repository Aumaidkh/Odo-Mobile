package com.hopcape.odo.feature.dashboard.domain.usecase

import com.hopcape.odo.core.domain.activity.analysis.ActivityFeedBuilder
import com.hopcape.odo.core.domain.alerts.analysis.AttentionPicker
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.cost.model.RunningCost
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.fairness.analysis.SavingsCalculator
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.insight.analysis.InsightPicker
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.dashboard.domain.model.HomeSnapshot
import com.hopcape.odo.feature.dashboard.domain.model.SetupProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Everything the Home tab shows, as a stream.
 *
 * Home is an aggregator: the score, the running cost, the savings, the attention card, the
 * insight and the recent row are each computed by a shared `:core:domain` rule, and this
 * use case only decides what those rules cannot see — which car, what day it is, and which
 * window the cost covers. A feature may not import a feature, so none of the maths is
 * borrowed from `:feature:healthscore` or `:feature:cost-tracker`; both read the same
 * calculators this does.
 *
 * **One combined read, not six.** Every card is a view of the same car at the same moment,
 * and separate streams emit at different moments — which is how a dial that already counted
 * a renewal ends up sitting above a card still demanding it. The sources are combined in two
 * stages only because `combine` runs out of typed overloads at five.
 *
 * Everything is observed rather than read once, because each source changes while the tab is
 * open: a service logged from the ledger, a paper filed in the vault, an odometer updated in
 * the garage, a fuel rate corrected on the cost screen.
 */
internal class ObserveHomeUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val documents: DocumentRepository,
    private val scores: HealthScoreRepository,
    private val owners: OwnerProfileRepository,
    private val city: CurrentCityProvider,
    private val fuelPrices: FuelPriceProvider,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<HomeSnapshot> = combine(
        record(carId),
        owners.observe(),
        // A price is neither the car nor its logs, so without watching it the ₹/km would
        // sit there built on a rate the owner has since corrected.
        fuelPrices.priceChanges(),
    ) { record, owner, _ ->
        snapshotOf(carId, record, ownerName = owner?.name?.value)
    }

    /** The car's whole record, read as one fact. */
    private fun record(carId: CarId): Flow<CarRecord> = combine(
        cars.observe(carId),
        logs.observe(carId),
        logs.observeOdometerReadings(carId),
        documents.observe(carId),
        scores.observeHistory(carId),
    ) { car, entries, readings, documents, history ->
        CarRecord(car, entries, readings, documents, history)
    }

    private suspend fun snapshotOf(
        carId: CarId,
        record: CarRecord,
        ownerName: String?,
    ): HomeSnapshot {
        // Resolved once per emission, so the score, the cost window and every deadline are
        // measured against the same day.
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date

        val cost = costOver(CostWindow.endingOn(today, COST_WINDOW_MONTHS), record, today)
        val score = HealthScoreCalculator.compute(
            today = today,
            entries = record.entries,
            documents = record.documents,
            readings = record.readings,
        )

        return HomeSnapshot(
            ownerName = ownerName,
            car = record.car,
            score = score,
            scoreDelta = score.deltaFrom(scores.latestOnOrBefore(carId, now - DELTA_WINDOW)?.score),
            cost = cost.current,
            costTrend = cost.trend,
            savings = SavingsCalculator.of(record.entries),
            attention = AttentionPicker.pick(
                documents = record.documents,
                entries = record.entries,
                readings = record.readings,
                today = today,
            ),
            insight = InsightPicker.pick(
                entries = record.entries,
                documents = record.documents,
                costTrend = cost.trend,
            ),
            // The same builder the Timeline tab renders in full, so the row Home shows is
            // the row that sits at the top of the timeline.
            recent = ActivityFeedBuilder.build(
                car = record.car,
                entries = record.entries,
                documents = record.documents,
                scores = record.history,
                zone = timeZone,
            ).firstOrNull(),
            setup = SetupProgress(
                carAdded = record.car != null,
                billScanned = record.entries.any { it.verification == VerificationStatus.VERIFIED },
                documentsFiled = record.documents.isNotEmpty(),
                hasServiceLogs = record.entries.isNotEmpty(),
            ),
        )
    }

    /**
     * The window's cost and the one before it, both priced off the same fuel rate.
     *
     * A car with no city set, or one in a city Odo carries no prices for, gets no rate at
     * all — the cost then covers maintenance only, and the screen says so rather than
     * presenting a partial figure as the whole.
     */
    private suspend fun costOver(window: CostWindow, record: CarRecord, today: LocalDate): CostPair {
        val fuelRate = record.car
            ?.let { fuelPrices.priceFor(city.currentCity(), it.fuelType) }
            ?.let(FuelEfficiencyPolicy::ratePerKm)
        return CostPair(
            current = compute(window, record, fuelRate),
            previous = compute(window.previous(), record, fuelRate),
        )
    }

    private fun compute(window: CostWindow, record: CarRecord, fuelRate: Amount?): RunningCost =
        RunningCostCalculator.compute(
            window = window,
            entries = record.entries,
            readings = record.readings,
            fuelRatePerKm = fuelRate,
        )

    /** One read of everything that has ever happened to the car. */
    private data class CarRecord(
        val car: Car?,
        val entries: List<ServiceLogEntry>,
        val readings: List<OdometerReading>,
        val documents: List<Document>,
        val history: List<HealthSnapshot>,
    )

    /** A window and its predecessor, which exists only to produce the trend. */
    private data class CostPair(val current: RunningCost, val previous: RunningCost) {
        val trend get() = current.trendAgainst(previous)
    }

    private companion object {

        /**
         * How much of the car's spending the headline ₹/km covers.
         *
         * A quarter is long enough to survive a month with no service in it and short
         * enough to still describe the car as it runs today. It is also what makes the
         * trend line read as "vs last quarter", against the quarter immediately before.
         */
        const val COST_WINDOW_MONTHS = 3

        /**
         * How far back "this month" reaches for the score delta. The baseline is the newest
         * snapshot that is already at least this old — not merely the previous one, which
         * could have been taken yesterday.
         */
        val DELTA_WINDOW = 30.days
    }
}
