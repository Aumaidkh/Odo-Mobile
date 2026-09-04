package com.hopcape.odo.feature.billcheck.domain.usecase

import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.catalog.SegmentCatalog
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.billcheck.domain.BillCheck
import com.hopcape.odo.feature.billcheck.domain.Evidence
import com.hopcape.odo.feature.billcheck.domain.FlaggedLine
import com.hopcape.odo.feature.billcheck.domain.PricedLine
import com.hopcape.odo.feature.billcheck.domain.Reason
import com.hopcape.odo.feature.billcheck.domain.matching.BillLineMatcher
import com.hopcape.odo.feature.billcheck.domain.matching.LineMatch

/** One line as it was printed on the bill. */
internal data class BillLine(val label: String, val amount: Amount)

/**
 * Decides what each line of a bill is worth asking about, on price alone.
 *
 * Only the rate claim. A repeat needs the owner's record and a schedule question needs the
 * maker's interval, and both are their own slice — this one answers "were you charged more
 * than this job normally costs here", which is the claim the reference tables can defend.
 *
 * **Nothing is flagged without a band.** A line whose job could not be named, or whose job
 * the tables carry no price for, comes back unchecked rather than fine: `fine` is drawn with
 * a tick and reads as "we checked this", and saying that about a line nobody priced would be
 * the app claiming work it did not do.
 */
internal class CheckBillPriceUseCase(
    private val matcher: BillLineMatcher,
    private val bands: PriceBandRepository,
) {

    suspend operator fun invoke(
        car: Car,
        city: String,
        workshop: WorkshopTier,
        lines: List<BillLine>,
        billTotal: Amount,
        canFlagRepeats: Boolean,
    ): BillCheck {
        val flagged = mutableListOf<FlaggedLine>()
        val fine = mutableListOf<PricedLine>()
        val unchecked = mutableListOf<PricedLine>()

        lines.forEach { line ->
            val priced = PricedLine(line.label, line.amount)
            when (val match = matcher.match(line.label)) {
                // Labour, tax, a discount. There is nothing to check it against, and the
                // modelled band already includes labour anyway.
                LineMatch.NotAJob, LineMatch.Unknown -> unchecked += priced

                is LineMatch.Job -> {
                    val band = bandFor(match, car, city, workshop)
                    when {
                        band == null -> unchecked += priced
                        line.amount.paise > band.high.paise ->
                            flagged += line.overpriced(band, workshop)

                        else -> fine += priced
                    }
                }
            }
        }

        return BillCheck(
            car = car.displayName(),
            city = city,
            workshop = workshop,
            billTotal = billTotal,
            // Strongest evidence first, then the biggest rupee figure — the owner reads from
            // the top and the top should be the line they can argue hardest.
            flagged = flagged.sortedWith(
                compareByDescending<FlaggedLine> { it.evidence.strength }
                    .thenByDescending { it.amount.paise },
            ),
            fine = fine,
            unchecked = unchecked,
            canFlagRepeats = canFlagRepeats,
        )
    }

    /**
     * The band, or null when there is none.
     *
     * **An unknown model sends no segment rather than a guessed one.** The server widens its
     * search when a field is missing, so an unlisted car gets a city-wide answer instead of a
     * hatchback's parts price — and a bill for an SUV priced as a hatchback is a finding put
     * in front of an owner that is wrong in the direction that costs them the argument.
     */
    private suspend fun bandFor(
        match: LineMatch.Job,
        car: Car,
        city: String,
        workshop: WorkshopTier,
    ): PriceBand? = bands.bandFor(
        PriceBandQuery(
            categorySlug = match.kind.slug,
            city = city,
            segment = SegmentCatalog.segmentOrNull(car.model),
            fuel = car.fuelType,
            workshopTier = workshop,
        ),
    ).getOrNull()

    /**
     * Charged above the band.
     *
     * The evidence rung is the band's basis, not its scope: the owner is being told how much
     * to trust the figure, and "computed from city rates" against "from 14 real bills" is
     * the distinction that answers that. Which SQL filter matched is on the "How we know"
     * sheet, where it is the point.
     */
    private fun BillLine.overpriced(band: PriceBand, workshop: WorkshopTier) = FlaggedLine(
        name = label,
        amount = amount,
        reason = Reason.AboveBand(low = band.low, high = band.high),
        evidence = when (band.basis) {
            BenchmarkBasis.MODELLED -> Evidence.CityRates
            BenchmarkBasis.OBSERVED -> Evidence.RealBills(band.sampleSize)
        },
        // No question to ask: the rate itself is the finding, and the band beside it is
        // already the whole argument.
        ask = null,
    )

    /** "Swift VXi" — what the header says, and never the plate. */
    private fun Car.displayName(): String = listOfNotNull(
        model.takeIf { it.isNotBlank() },
        variant?.takeIf { it.isNotBlank() },
    ).joinToString(" ")
}
