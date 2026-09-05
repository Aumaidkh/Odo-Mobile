package com.hopcape.odo.feature.billcheck.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.catalog.SegmentCatalog
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.city.CityCatalog
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.WorkshopTier
import kotlinx.coroutines.flow.first

/**
 * Where one line's band came from.
 *
 * The screen this feeds is the feature's whole argument: a band nobody can interrogate is a
 * number to be taken on faith, and the product exists to stop an owner doing that at a
 * counter. So every input is shown, and the rung that answered is named rather than the
 * narrowest one implied.
 *
 * It asks the same question the check asked, with the same inputs, rather than being handed
 * the check's answer. Two reads of one table beat a cache that can disagree with the screen
 * behind it — and this one runs only when the owner taps, which is rarely.
 *
 * **No band, no sheet.** A line the tables cannot price has no working to show, and an
 * invented one is exactly the unsourced figure being argued against.
 */
internal class LoggedBandBasisReader(
    private val entries: ServiceLogRepository,
    private val cars: CarRepository,
    private val cities: CurrentCityProvider,
    private val cityCatalog: CityCatalog,
    private val questionnaire: QuestionnaireRepository,
    private val bands: PriceBandRepository,
) : BandBasisReader {

    /**
     * The band behind one finding.
     *
     * [categorySlug] is the job the check already resolved, passed in rather than worked out
     * again. This screen used to name the line itself — with the rules alone, so a line the
     * *model* had named came back unnamed and the sheet refused to explain a finding the app
     * had just drawn. Re-deriving it with the model instead would only have moved the fault:
     * a second answer can differ from the one the band was built from.
     */
    override suspend fun basisFor(
        billId: String,
        lineName: String,
        categorySlug: String,
    ): Either<DomainError, BandBasis> {
        val entry = entries.observe(ServiceLogId(billId)).first()
            ?: return DomainError.ServiceLogNotFound.left()
        val car = cars.observe(entry.carId).first() ?: return DomainError.CarNotFound.left()
        val city = cities.currentCity() ?: return DomainError.LookupUnavailable.left()
        val workshop = workshopTier()
        val band = bands.bandFor(
            PriceBandQuery(
                categorySlug = categorySlug,
                city = city,
                segment = SegmentCatalog.segmentOrNull(car.model),
                fuel = car.fuelType,
                workshopTier = workshop,
            ),
        ).getOrNull() ?: return DomainError.LookupUnavailable.left()

        return BandBasis(
            lineName = lineName,
            low = band.low,
            high = band.high,
            city = city,
            cityTier = tierOf(city),
            workshop = workshop,
            segment = car.segmentLabel(),
            // Zero where the band came from real bills: there is no sum behind what people
            // actually paid, and the sheet shows the rung that says so.
            labourRatePerHour = band.working?.labourRatePerHour ?: Amount.ZERO,
            labourHours = band.working?.labourHours ?: 0.0,
            rungs = band.ladder(),
        ).right()
    }

    /**
     * The owner's city tier, or null when the local catalogue does not carry the city.
     *
     * Never a default. The server resolves the tier for itself when it builds the band, so a
     * fallback here would show a tier the band was not built at — on the sheet that exists to
     * show what it *was* built at.
     */
    private suspend fun tierOf(city: String): Int? =
        cityCatalog.cities()
            .firstOrNull { it.name.equals(city, ignoreCase = true) }
            ?.tier

    private suspend fun workshopTier(): WorkshopTier {
        val answer = questionnaire.answersFor(QuestionKeys.Workshop).getOrNull()?.firstOrNull()
        return WorkshopTier.entries.firstOrNull { it.name == answer?.value } ?: WorkshopTier.MULTI_BRAND
    }

    /** "petrol hatchback" — what the tables were asked for, in the owner's words. */
    private fun Car.segmentLabel(): String = listOfNotNull(
        fuelType.name.lowercase(),
        SegmentCatalog.segmentOrNull(model)?.name?.lowercase(),
    ).joinToString(" ")

    /**
     * The three rungs the sheet draws, from the six the server walks.
     *
     * The rung that answered is [RungState.USED]; anything narrower had nothing, which is why
     * the search widened, and anything wider was never reached. Six filter combinations named
     * on screen would explain the query rather than the answer.
     */
    private fun PriceBand.ladder(): List<Rung> {
        val answered = when {
            // Real bills at this narrowness are as close as the pool gets to this car at this
            // kind of centre.
            scope == BenchmarkScope.CITY_TIER_SEGMENT && basis == BenchmarkBasis.OBSERVED ->
                BandScope.THIS_CAR_THIS_CENTRE

            scope == BenchmarkScope.METRO_TIER || scope == BenchmarkScope.NATIONAL_TIER ->
                BandScope.NATIONAL

            else -> BandScope.CITY_TIER_SEGMENT
        }
        val order = listOf(
            BandScope.THIS_CAR_THIS_CENTRE,
            BandScope.CITY_TIER_SEGMENT,
            BandScope.NATIONAL,
        )
        val answeredAt = order.indexOf(answered)
        return order.mapIndexed { index, scope ->
            Rung(
                scope = scope,
                state = when {
                    index < answeredAt -> RungState.NO_DATA
                    index == answeredAt -> RungState.USED
                    else -> RungState.NOT_NEEDED
                },
            )
        }
    }

}
