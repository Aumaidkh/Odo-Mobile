package com.hopcape.odo.feature.advisory.domain.checklist

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.catalog.SegmentCatalog
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.schedule.ServiceIntervalRepository
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.AmountRange
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.WorkshopTier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Everything the "Before you go in" screen renders, read once.
 *
 * A one-shot read rather than a stream: the bands come off the network, and a flow that
 * re-emitted on every logged service would re-ask the price tables while the owner is
 * standing at a counter reading the answer.
 *
 * Nothing here fails on a missing part. No city means no band, which costs the cost line and
 * leaves the checklist itself whole. Only a missing car is fatal — there is no service to
 * prepare for.
 */
internal class ReadServiceChecklistUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val odometers: CurrentOdometerProvider,
    private val schedule: ServiceIntervalRepository,
    private val bands: PriceBandRepository,
    private val cities: CurrentCityProvider,
    private val questionnaire: QuestionnaireRepository,
    private val builder: PreServiceChecklistBuilder,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    suspend operator fun invoke(): Either<DomainError, ServiceChecklist> {
        val car = cars.observePrimaryCar().first() ?: return DomainError.CarNotFound.left()
        val today = clock.now().toLocalDateTime(timeZone).date
        val currentKm = odometers.observeCurrent(car.id).first()?.km ?: car.odometer.km
        val history = logs.observe(car.id).first()

        // A schedule that could not be read is no schedule. Reported rather than logged, the
        // way this module already reports an unresolved city: the use case stays free of
        // observability and the ViewModel, which owns the feature's trace, says it once.
        val read = schedule.intervals(car.make)
        val intervals = read.getOrNull().orEmpty()
        val checklist = builder.build(
            schedule = intervals,
            history = history,
            currentKm = currentKm,
            carAddedOn = car.addedOn,
            today = today,
        )

        return ServiceChecklist(
            carName = car.displayName,
            ageYears = car.ageIn(today),
            odometerKm = currentKm,
            checklist = checklist,
            cost = costOf(checklist.due, car),
            scheduleUnavailable = read.isLeft(),
        ).right()
    }

    /**
     * What the due jobs should add up to.
     *
     * Only the jobs a band was found for, and the count says how many that was. Track A is
     * still being typed, so a total that silently skipped two jobs would be quoted at a
     * counter as though it covered them.
     */
    private suspend fun costOf(due: List<ChecklistItem>, car: Car): ChecklistCost? {
        val city = cities.currentCity() ?: return null
        val tier = workshopTier()
        val segment = SegmentCatalog.segmentOrNull(car.model)

        // Asked together, not one after another. Each is a network RPC, and a car with ten
        // due jobs would otherwise hold the screen through ten serial round trips while the
        // owner stands at a counter waiting for it.
        val found = coroutineScope {
            due.map { item ->
                async {
                    bands.bandFor(
                        PriceBandQuery(
                            categorySlug = item.slug,
                            city = city,
                            segment = segment,
                            fuel = car.fuelType,
                            workshopTier = tier,
                        ),
                    ).getOrNull()
                }
            }.awaitAll()
        }.filterNotNull()

        val low = found.sumOf { it.low.paise }
        val high = found.sumOf { it.high.paise }
        val priced = found.size
        if (priced == 0) return null
        return ChecklistCost(
            range = AmountRange.ofPaise(low, high),
            pricedItems = priced,
            dueItems = due.size,
        )
    }

    /**
     * Where the owner said they get the car serviced — the labour rate every band is quoted
     * at. Unanswered falls to the middle tier, the least wrong answer when it is unknown.
     */
    private suspend fun workshopTier(): WorkshopTier {
        val answer = questionnaire.answersFor(QuestionKeys.Workshop).getOrNull()?.firstOrNull()
        return WorkshopTier.entries.firstOrNull { it.name == answer?.value } ?: DEFAULT_TIER
    }

    private fun Car.ageIn(today: LocalDate): Int? =
        (purchaseYear?.value ?: year.value).let { (today.year - it).takeIf { age -> age >= 0 } }

    private companion object {
        val DEFAULT_TIER = WorkshopTier.MULTI_BRAND
    }
}

/** The screen's whole content. */
internal data class ServiceChecklist(
    val carName: String,
    /** Null when neither a purchase year nor a model year gives a sane age. */
    val ageYears: Int?,
    val odometerKm: Int,
    val checklist: PreServiceChecklist,
    /** Null when there is no city, or no due job the tables carry a price for. */
    val cost: ChecklistCost?,
    /**
     * The schedule could not be read at all, so the list is missing rather than short.
     * The two look identical on screen and mean opposite things to whoever is on call.
     */
    val scheduleUnavailable: Boolean = false,
)

/**
 * What the due work should cost, and how much of it the figure covers.
 *
 * [pricedItems] travels with the range because it is what makes the range honest: a total
 * over three of five jobs is useful, and the same total presented as all five is not.
 */
internal data class ChecklistCost(
    val range: AmountRange,
    val pricedItems: Int,
    val dueItems: Int,
)
