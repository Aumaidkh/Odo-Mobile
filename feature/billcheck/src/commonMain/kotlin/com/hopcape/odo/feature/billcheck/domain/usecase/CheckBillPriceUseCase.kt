package com.hopcape.odo.feature.billcheck.domain.usecase

import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.catalog.SegmentCatalog
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.schedule.ServiceIntervalRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.billcheck.domain.BillCheck
import com.hopcape.odo.feature.billcheck.domain.Evidence
import com.hopcape.odo.feature.billcheck.domain.FlaggedLine
import com.hopcape.odo.feature.billcheck.domain.PricedLine
import com.hopcape.odo.feature.billcheck.domain.Reason
import com.hopcape.odo.feature.billcheck.domain.matching.BillLineMatcher
import com.hopcape.odo.feature.billcheck.domain.matching.LineMatch
import kotlinx.datetime.LocalDate

/** One line as it was printed on the bill. */
internal data class BillLine(val label: String, val amount: Amount)

/**
 * Decides what each line of a bill is worth asking about.
 *
 * Two claims, and the record wins. A job the owner's own history already shows is the
 * strongest thing Odo can say, so it is checked first and the rate claim is not made about
 * the same line — one finding per line, and the better-evidenced one.
 *
 * **Nothing is flagged without a band.** A line whose job could not be named, or whose job
 * the tables carry no price for, comes back unchecked rather than fine: `fine` is drawn with
 * a tick and reads as "we checked this", and saying that about a line nobody priced would be
 * the app claiming work it did not do.
 */
internal class CheckBillPriceUseCase(
    private val matcher: BillLineMatcher,
    private val bands: PriceBandRepository,
    private val intervals: ServiceIntervalRepository,
) {

    suspend operator fun invoke(
        car: Car,
        city: String,
        workshop: WorkshopTier,
        lines: List<BillLine>,
        billTotal: Amount,
        billDate: LocalDate,
        history: List<ServiceLogEntry>,
    ): BillCheck {
        val repeats = RepeatFinder(
            matcher = matcher,
            // A schedule that could not be read is no schedule: every job falls back to the
            // stated default rather than the screen losing its repeat findings entirely.
            intervals = intervals.intervals().getOrNull().orEmpty(),
        )
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
                    val repeat = repeats.previous(match.kind, history, billDate)
                    val band = bandFor(match, car, city, workshop)
                    when {
                        // The record beats the table. It is the owner's own data, and "you had
                        // this in April" is a harder question than "this looks dear".
                        repeat != null -> flagged += line.repeated(repeat)
                        band == null -> unchecked += priced
                        line.amount.paise > band.high.paise -> flagged += line.overpriced(band)
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
            // Nothing to compare against yet, so the screen says what adding a bill buys.
            canFlagRepeats = history.isNotEmpty(),
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

    /** Done again, sooner than it comes round. */
    private fun BillLine.repeated(repeat: RepeatFinder.Repeat) = FlaggedLine(
        name = label,
        amount = amount,
        reason = Reason.DoneRecently(monthsAgo = repeat.monthsAgo, on = repeat.on.toString()),
        evidence = Evidence.OwnRecord,
    )

    /**
     * Charged above the band.
     *
     * The evidence rung is the band's basis, not its scope: the owner is being told how much
     * to trust the figure, and "computed from city rates" against "from 14 real bills" is the
     * distinction that answers that. Which SQL filter matched is on the "How we know" sheet,
     * where it is the point.
     */
    private fun BillLine.overpriced(band: PriceBand) = FlaggedLine(
        name = label,
        amount = amount,
        reason = Reason.AboveBand(low = band.low, high = band.high),
        evidence = when (band.basis) {
            BenchmarkBasis.MODELLED -> Evidence.CityRates
            BenchmarkBasis.OBSERVED -> Evidence.RealBills(band.sampleSize)
        },
    )

    /** "Swift VXi" — what the header says, and never the plate. */
    private fun Car.displayName(): String = listOfNotNull(
        model.takeIf { it.isNotBlank() },
        variant?.takeIf { it.isNotBlank() },
    ).joinToString(" ")
}
