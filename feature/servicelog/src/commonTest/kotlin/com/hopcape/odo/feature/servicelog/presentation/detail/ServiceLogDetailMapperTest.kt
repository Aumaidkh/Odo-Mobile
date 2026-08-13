package com.hopcape.odo.feature.servicelog.presentation.detail

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.servicelog.domain.usecase.ServiceEntryDetail
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the detail screen is allowed to show about a bill's lines, and what it may offer to
 * do with them.
 *
 * Two rules. An entry's priced lines survive to the screen whatever the fairness check said
 * about them (issue #109): a verdict annotates a line, it never replaces the list, and it is
 * never attached to a line it was not taken for. And the "Check fairness" action is offered
 * only when there is something to benchmark (issue #111).
 */
class ServiceLogDetailMapperTest {

    @Test
    fun assessedEntry_keepsEveryBilledLine_andPricesTheOnesTheCityCouldJudge() {
        val entry = entry(
            lines = listOf(
                line("Engine oil", ServiceCategory.OIL_CHANGE, 340_000),
                line("Windscreen wiper", ServiceCategory.OTHER, 60_000),
            ),
            benchmarks = mapOf(ServiceCategory.OIL_CHANGE to 290_000L),
        )

        val items = entry.toDetail().toUiState().lineItems

        assertEquals(listOf("Engine oil", "Windscreen wiper"), items.map { it.label })
        assertEquals(rupees(290_000), items[0].cityAverage)
        assertIs<FairnessVerdict.Over>(items[0].verdict)
        // Unbenchmarked, and it stays on screen saying exactly that — this is the line the
        // old screen dropped once a verdict existed.
        assertNull(items[1].cityAverage)
        assertNull(items[1].verdict)
    }

    @Test
    fun unassessedEntry_stillShowsItsBilledLines() {
        val entry = entry(lines = listOf(line("Engine oil", ServiceCategory.OIL_CHANGE, 340_000)))

        val items = entry.toDetail().toUiState().lineItems

        assertEquals(listOf("Engine oil"), items.map { it.label })
        assertNull(items.single().verdict)
    }

    @Test
    fun entryWithNoLines_mapsToNoLines_soTheScreenCanSaySoInsteadOfShowingAnEmptyCard() {
        val entry = entry(lines = emptyList())

        assertTrue(entry.toDetail().toUiState().lineItems.isEmpty())
    }

    @Test
    fun aVerdictTakenOnDifferentLines_isNotPinnedOntoTheseOnes() {
        // The snapshot judged one line; the entry now carries two — an edit after the check.
        // Nothing about that pairing is provable, so no line borrows a comparison.
        val judged = FairnessReport.of(
            query = FairnessQuery(
                city = CITY,
                items = listOf(FairnessQueryItem("Engine oil", ServiceCategory.OIL_CHANGE, rupees(340_000))),
            ),
            estimates = mapOf(ServiceCategory.OIL_CHANGE to estimate(ServiceCategory.OIL_CHANGE, 290_000)),
        )
        val entry = entry(
            lines = listOf(
                line("Engine oil", ServiceCategory.OIL_CHANGE, 340_000),
                line("Labour", ServiceCategory.OIL_CHANGE, 60_000),
            ),
            snapshot = FairnessSnapshot(report = judged, checkedAt = CHECKED_AT),
        )

        val items = entry.toDetail().toUiState().lineItems

        assertEquals(2, items.size)
        assertTrue(items.all { it.cityAverage == null && it.verdict == null })
    }

    @Test
    fun entryWithPricedLines_offersTheFairnessCheck() {
        val entry = entry(lines = listOf(line("Engine oil", ServiceCategory.OIL_CHANGE, 340_000)))

        assertTrue(entry.toDetail().toUiState().canCheckFairness)
    }

    @Test
    fun entryWithNoLines_butOneJob_offersTheCheckOnItsTotal() {
        val entry = entry(lines = emptyList(), categories = setOf(ServiceCategory.OIL_CHANGE))

        assertTrue(entry.toDetail().toUiState().canCheckFairness)
    }

    @Test
    fun entryWithNoLines_andSeveralJobs_doesNotOfferTheCheck() {
        // One total covering two jobs cannot be split, so there is nothing to benchmark.
        // The screen used to offer the button anyway and the tap was refused in silence
        // (issue #111).
        val entry = entry(
            lines = emptyList(),
            categories = setOf(ServiceCategory.OIL_CHANGE, ServiceCategory.OTHER),
        )

        assertFalse(entry.toDetail().toUiState().canCheckFairness)
    }

    private fun ServiceLogEntry.toDetail() = ServiceEntryDetail(entry = this, recordScoreUplift = 4)

    private fun entry(
        lines: List<ServiceLogLineItem>,
        benchmarks: Map<ServiceCategory, Long> = emptyMap(),
        snapshot: FairnessSnapshot? = null,
        categories: Set<ServiceCategory> = emptySet(),
    ): ServiceLogEntry = ServiceLogEntry.reconstitute(
        id = ServiceLogId("log-1"),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = LocalDate(2026, 3, 2),
        odometerKm = 48_500,
        totalAmountPaise = 400_000,
        workshopName = "AutoCare Pune",
        notes = null,
        source = LogSource.SCANNED,
        billId = null,
        categories = categories,
        lineItems = lines,
        billPhotoRef = "bills/car-1/log-1.jpg",
        fairness = snapshot ?: snapshotOf(lines, benchmarks),
    )

    /** The verdict as it would have been stored: the entry's own lines, in their own order. */
    private fun snapshotOf(
        lines: List<ServiceLogLineItem>,
        benchmarks: Map<ServiceCategory, Long>,
    ): FairnessSnapshot? {
        if (benchmarks.isEmpty()) return null
        val report = FairnessReport.of(
            query = FairnessQuery(
                city = CITY,
                items = lines.map { FairnessQueryItem(it.label, it.category, it.amount) },
            ),
            estimates = benchmarks.mapValues { (category, average) -> estimate(category, average) },
        )
        return FairnessSnapshot(report = report, checkedAt = CHECKED_AT)
    }

    private fun estimate(category: ServiceCategory, averagePaise: Long) = FairnessEstimate(
        category = category,
        city = CITY,
        cityAverage = rupees(averagePaise),
        sampleSize = 240,
    )

    private fun line(label: String, category: ServiceCategory, paise: Long) =
        ServiceLogLineItem(label = label, category = category, amount = rupees(paise))

    private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }

    private companion object {
        const val CITY = "Pune"
        val CHECKED_AT: Instant = Instant.parse("2026-03-03T10:00:00Z")
    }
}
