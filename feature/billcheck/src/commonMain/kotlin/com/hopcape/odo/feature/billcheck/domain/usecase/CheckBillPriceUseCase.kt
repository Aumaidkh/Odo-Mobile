package com.hopcape.odo.feature.billcheck.domain.usecase

import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.advisory.BillLineClassifier
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.benchmark.PriceObservation
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
import com.hopcape.odo.feature.billcheck.domain.matching.JobKind
import com.hopcape.odo.feature.billcheck.domain.matching.LineMatch
import kotlinx.datetime.LocalDate

/** One line as it was printed on the bill. */
internal data class BillLine(val label: String, val amount: Amount)

/**
 * The check, and the prices it is willing to give back to the pool.
 *
 * Only lines whose job was named and whose band was found: a price filed against the wrong
 * job comes back to some other owner as a band, so a guess here is worse than a gap.
 */
internal data class CheckedBill(
    val check: BillCheck,
    val observations: List<PriceObservation>,
)

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
 *
 * The rules run first and the model only sees what they could not name. It names a job and
 * never a price — the band comes from the tables either way — and what it names is kept out
 * of the shared pool (see [namedByModel]).
 */
internal class CheckBillPriceUseCase(
    private val matcher: BillLineMatcher,
    private val bands: PriceBandRepository,
    private val intervals: ServiceIntervalRepository,
    private val classifier: BillLineClassifier,
    private val config: FeatureConfig,
) {

    suspend operator fun invoke(
        car: Car,
        /**
         * Where the owner is. Null when they never set one — no band can be asked for then,
         * and the check falls back to what it knows without a city: their own record and the
         * maker's schedule. A worse answer, and far better than no screen.
         */
        city: String?,
        workshop: WorkshopTier,
        lines: List<BillLine>,
        billTotal: Amount,
        billDate: LocalDate,
        /** The reading on the bill, not on the car today. */
        odometerKm: Int,
        history: List<ServiceLogEntry>,
    ): CheckedBill {
        // A schedule that could not be read is no schedule: repeats fall back to the stated
        // default and schedule claims are simply not made, rather than the screen losing
        // every finding it had.
        val schedule = intervals.intervals().getOrNull().orEmpty()
        val repeats = RepeatFinder(matcher = matcher, intervals = schedule)
        val notDue = ScheduleChecker(matcher = matcher, intervals = schedule)
        val flagged = mutableListOf<FlaggedLine>()
        val fine = mutableListOf<PricedLine>()
        val unchecked = mutableListOf<PricedLine>()
        val observations = mutableListOf<PriceObservation>()

        val matched = lines.map { it to matcher.match(it.label) }
        val byModel = namedByModel(matched)

        matched.forEach { (line, match) ->
            val priced = PricedLine(line.label, line.amount)
            val kind = when (match) {
                // Labour, tax, a discount. There is nothing to check it against, and the
                // modelled band already includes labour anyway.
                LineMatch.NotAJob -> null
                LineMatch.Unknown -> byModel[line.label]
                is LineMatch.Job -> match.kind
            }
            if (kind == null) {
                unchecked += priced
                return@forEach
            }

            val repeat = repeats.previous(kind, history, billDate)
            val early = notDue.notDueYet(kind, odometerKm, history)
            val band = bandFor(kind, car, city, workshop)
            // Every named line with a band is a real price somebody paid, whatever else was
            // said about it — a repeat is still a price, and the pool is about what things
            // cost rather than about who should have asked.
            //
            // **Unless the model named it.** A price filed against the wrong job comes back to
            // some other owner as their band, and the rules are the only naming this can
            // defend to a stranger. The owner gets their answer either way; the pool takes
            // only what a phrase in a table stands behind.
            if (band != null && city != null && match is LineMatch.Job) {
                observations += line.observed(kind, car, city, workshop)
            }
            when {
                // The record beats both tables. It is the owner's own data, and "you had this
                // in April" is a harder question than anything else here.
                repeat != null -> flagged += line.repeated(repeat)
                // Then the maker's schedule, which is a published fact, ahead of a band that
                // is often a calculation.
                early != null -> flagged += line.notDueYet(early)
                band == null -> unchecked += priced
                line.amount.paise > band.high.paise -> flagged += line.overpriced(band)
                else -> fine += priced
            }
        }

        return CheckedBill(observations = observations, check = BillCheck(
            car = car.displayName(),
            city = city.orEmpty(),
            workshop = workshop,
            billTotal = billTotal,
            // Strongest evidence first, then the biggest rupee figure — the owner reads from
            // the top and the top should be the line they can argue hardest.
            flagged = flagged.sortedWith(
                // A schedule claim has no rung, and sits with the modelled bands rather than
                // last: it is a published fact, not the weakest thing on the screen.
                compareByDescending<FlaggedLine> { it.evidence?.strength ?: SCHEDULE_RANK }
                    .thenByDescending { it.amount.paise },
            ),
            fine = fine,
            unchecked = unchecked,
            // Nothing to compare against yet, so the screen says what adding a bill buys.
            canFlagRepeats = history.isNotEmpty(),
        ))
    }

    /**
     * The jobs the rules could not name, named by the model.
     *
     * Only [LineMatch.Unknown] is sent. A [LineMatch.NotAJob] line is one the rules were
     * certain about, and a model that read "labour charges for AC service" as an AC service
     * would price a whole job against a labour line.
     *
     * Off by default — see [FeatureConfig.advisoryClassifierEnabled]. Off, nothing leaves the
     * device and those lines stay unchecked, which is what they do today. Asked here rather
     * than at construction so a flip lands on the next check.
     *
     * A slug this app has no [JobKind] for is dropped. The server's catalogue is the longer
     * list, and a slug nothing here can look up is not an answer.
     */
    private suspend fun namedByModel(
        matched: List<Pair<BillLine, LineMatch>>,
    ): Map<String, JobKind> {
        if (!config.advisoryClassifierEnabled) return emptyMap()
        val unknown = matched
            .filter { (_, match) -> match == LineMatch.Unknown }
            .map { (line, _) -> line.label }
            .filterNot { it.looksPersonal() }
            .distinct()
        if (unknown.isEmpty()) return emptyMap()

        val bySlug = JobKind.entries.associateBy { it.slug }
        return classifier.classify(unknown)
            .mapNotNull { (label, slug) -> bySlug[slug]?.let { label to it } }
            .toMap()
    }

    /** What this line says about what a job costs here. Never who paid it. */
    private fun BillLine.observed(
        kind: JobKind,
        car: Car,
        city: String,
        workshop: WorkshopTier,
    ) = PriceObservation(
        categorySlug = kind.slug,
        city = city,
        amount = amount,
        segment = SegmentCatalog.segmentOrNull(car.model),
        fuel = car.fuelType,
        workshopTier = workshop,
        carMake = car.make,
    )

    /**
     * The band, or null when there is none.
     *
     * **An unknown model sends no segment rather than a guessed one.** The server widens its
     * search when a field is missing, so an unlisted car gets a city-wide answer instead of a
     * hatchback's parts price — and a bill for an SUV priced as a hatchback is a finding put
     * in front of an owner that is wrong in the direction that costs them the argument.
     */
    private suspend fun bandFor(
        kind: JobKind,
        car: Car,
        city: String?,
        workshop: WorkshopTier,
    ): PriceBand? = bands.bandFor(
        PriceBandQuery(
            categorySlug = kind.slug,
            city = city ?: return null,
            segment = SegmentCatalog.segmentOrNull(car.model),
            fuel = car.fuelType,
            workshopTier = workshop,
        ),
    ).getOrNull()

    /** Done again, sooner than it comes round. */
    private fun BillLine.repeated(repeat: RepeatFinder.Repeat) = FlaggedLine(
        name = label,
        amount = amount,
        reason = Reason.DoneRecently(monthsAgo = repeat.monthsAgo, on = repeat.on),
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
    /**
     * Not due yet by the maker's schedule.
     *
     * No evidence rung: this says nothing about the price, and the dots rank price evidence.
     */
    private fun BillLine.notDueYet(notDue: ScheduleChecker.NotDue) = FlaggedLine(
        name = label,
        amount = amount,
        reason = Reason.ScheduledLater(dueAtKm = notDue.dueAtKm, currentKm = notDue.currentKm),
        evidence = null,
    )

    private fun BillLine.overpriced(band: PriceBand) = FlaggedLine(
        name = label,
        amount = amount,
        reason = Reason.AboveBand(low = band.low, high = band.high),
        evidence = when (band.basis) {
            BenchmarkBasis.MODELLED -> Evidence.CityRates
            BenchmarkBasis.OBSERVED -> Evidence.RealBills(band.sampleSize)
        },
    )

    /**
     * Whether this line carries something about the owner rather than about a job.
     *
     * Line labels come from OCR, and a header the scanner read as a line item can carry the
     * plate, a phone number or an email. A line like that names no job anyway, so the only
     * thing sending it achieves is putting it in a server-side table that keeps it. Dropped
     * before the request rather than redacted after it.
     */
    private fun String.looksPersonal(): Boolean = PERSONAL.any { it.containsMatchIn(this) }

    private companion object {
        /** Where a schedule claim sorts among the price rungs: above a city estimate. */
        const val SCHEDULE_RANK = 2

        /** An Indian registration number, a ten-digit phone, an email. */
        val PERSONAL = listOf(
            Regex("""\b[A-Za-z]{2}[\s-]?\d{1,2}[\s-]?[A-Za-z]{0,3}[\s-]?\d{4}\b"""),
            Regex("""\b\d{10}\b"""),
            Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""),
        )
    }

    /** "Swift VXi" — what the header says, and never the plate. */
    private fun Car.displayName(): String = listOfNotNull(
        model.takeIf { it.isNotBlank() },
        variant?.takeIf { it.isNotBlank() },
    ).joinToString(" ")
}
