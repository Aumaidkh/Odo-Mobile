package com.hopcape.odo.feature.garage.domain.usecase

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.cost.model.RunningCost
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.model.latestOfType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.record.analysis.ServiceRecordBuilder
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Everything the printed vehicle-details document says about a car, in one value.
 *
 * [record] carries what the shared [ServiceRecordBuilder] already assembles — the car's
 * identity, the ownership block, each paper's validity and the service rows — so this
 * document and the service-record PDF can never disagree about a shared fact. The rest is
 * what the record does not hold: the score's factor breakdown, the running cost, the
 * papers' own titles, and the owner's city.
 */
internal data class CarDetails(
    val record: ServiceRecord,
    /** The latest score's breakdown; empty for a car nobody has scored. */
    val factors: List<HealthFactor>,
    /** The last twelve months' cost, or `null` while the car is still loading. */
    val runningCost: RunningCost?,
    /** The newest paper of each type, with the owner's own label for it. */
    val documents: List<CarDetailsDocument>,
    /** The owner's home city, or `null` when they never set one. */
    val city: String?,
)

/** One paper as the document's table prints it. */
internal data class CarDetailsDocument(
    val type: DocumentType,
    /** The owner's own label ("SafeDrive comprehensive"), or `null` to fall back to the type. */
    val title: String?,
    val validity: DocumentValidity,
)

/**
 * The vehicle-details export as a stream — what the garage's export sheet renders to a PDF.
 *
 * Observed rather than read once for the same reason as the service record's stream: the
 * sheet stays open while the owner picks a target, and an odometer updated or a paper filed
 * in that time belongs in the file they are about to send. The fuel price is watched too,
 * because the owner can correct it from the cost screen and every ₹/km built on the old
 * rate would sit stale.
 *
 * The running cost is computed over a fixed twelve-month window — the document states one
 * figure, and a year is the window a buyer reading it will assume.
 */
internal class ObserveCarDetailsUseCase(
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
    operator fun invoke(carId: CarId): Flow<CarDetails> = combine(
        // Two nested combines only because `combine` takes at most five flows; the split
        // carries no meaning.
        combine(
            cars.observe(carId),
            logs.observe(carId),
            logs.observeOdometerReadings(carId),
        ) { car, entries, readings -> Triple(car, entries, readings) },
        combine(
            documents.observe(carId),
            scores.observeHistory(carId),
            owners.observe(),
            fuelPrices.priceChanges(),
        ) { papers, history, owner, _ -> Triple(papers, history, owner) },
    ) { (car, entries, readings), (papers, history, owner) ->
        build(car, entries, readings, papers, history, owner)
    }

    private suspend fun build(
        car: Car?,
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        papers: List<Document>,
        history: List<HealthSnapshot>,
        owner: OwnerProfile?,
    ): CarDetails {
        // Read once per emission, so a sheet left open across midnight still dates the
        // document the day it was produced.
        val today = clock.now().toLocalDateTime(timeZone).date
        val ownerCity = city.currentCity()

        return CarDetails(
            record = ServiceRecordBuilder.build(
                car = car,
                owner = owner,
                entries = entries,
                documents = papers,
                scores = history,
                today = today,
                zone = timeZone,
            ),
            factors = history.maxByOrNull { it.computedAt }?.score?.factors.orEmpty(),
            runningCost = car?.let { runningCostOf(it, entries, readings, ownerCity, today) },
            documents = papers.onFile(today),
            city = ownerCity,
        )
    }

    /**
     * The last twelve months through the app's only per-km math. A car whose city has no
     * known fuel price gets a maintenance-only figure, which is honest — the document marks
     * the fuel line estimated for exactly this reason.
     */
    private suspend fun runningCostOf(
        car: Car,
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        ownerCity: String?,
        today: LocalDate,
    ): RunningCost {
        val fuelPrice = fuelPrices.priceFor(ownerCity, car.fuelType)
        return RunningCostCalculator.compute(
            window = CostWindow.endingOn(today, months = COST_WINDOW_MONTHS),
            entries = entries,
            readings = readings,
            fuelRatePerKm = fuelPrice?.let(FuelEfficiencyPolicy::ratePerKm),
        )
    }

    /**
     * The newest paper of each printed type, in print order — the same selection and the
     * same order as the service record's own table, minus the licence for the same reason:
     * it is the owner's paper, not the car's.
     */
    private fun List<Document>.onFile(today: LocalDate): List<CarDetailsDocument> =
        PRINTED_DOCUMENTS.mapNotNull { type ->
            latestOfType(type)?.let { document ->
                CarDetailsDocument(
                    type = type,
                    title = document.title?.value,
                    validity = document.validity(today),
                )
            }
        }

    private companion object {
        val PRINTED_DOCUMENTS = listOf(
            DocumentType.INSURANCE,
            DocumentType.PUC,
            DocumentType.RC,
            DocumentType.LOAN,
            DocumentType.OTHER,
        )

        const val COST_WINDOW_MONTHS = 12
    }
}
