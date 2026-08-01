package com.hopcape.odo.core.domain.health.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HealthScoreCalculatorTest {

    private val today = LocalDate(2026, 8, 1)

    // --- fixtures ---

    private fun amount(paise: Long) = Amount.of(paise).getOrElse { error("test fixture paise=$paise") }

    private fun entry(
        id: String,
        date: LocalDate,
        km: Int,
        paise: Long = 300_000,
        verified: Boolean = false,
        fairness: FairnessSnapshot? = null,
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = date,
        odometerKm = km,
        totalAmountPaise = paise,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
        fairness = fairness,
    )

    private fun reading(date: LocalDate, km: Int, logId: String? = null) = OdometerReading(
        logId = logId?.let(::ServiceLogId),
        date = date,
        odometer = Distance.of(km).getOrElse { error("test fixture km=$km") },
    )

    private fun document(type: DocumentType, expiresOn: LocalDate?, id: String = "doc-$type") =
        Document.reconstitute(
            id = DocumentId(id),
            ownerId = OwnerId("owner-1"),
            carId = CarId("car-1"),
            type = type,
            storagePath = "documents/$id.jpg",
            source = DocumentSource.UPLOADED,
            expiresOn = expiresOn,
        )

    /** A stored verdict, built the way the fairness check builds one. */
    private fun snapshot(paidPaise: Long, cityAveragePaise: Long, sampleSize: Int = 30): FairnessSnapshot {
        val estimate = FairnessEstimate(
            category = ServiceCategory.GENERAL_SERVICE,
            city = "Pune",
            cityAverage = amount(cityAveragePaise),
            sampleSize = sampleSize,
        )
        return FairnessSnapshot(
            report = FairnessReport(
                city = "Pune",
                items = listOf(
                    FairnessReportItem(
                        label = null,
                        category = ServiceCategory.GENERAL_SERVICE,
                        amount = amount(paidPaise),
                        estimate = estimate,
                        verdict = FairnessVerdict.of(amount(paidPaise), estimate),
                    ),
                ),
            ),
            checkedAt = Instant.fromEpochSeconds(0),
        )
    }

    private fun fairVerdict() = snapshot(paidPaise = 300_000, cityAveragePaise = 300_000)
    private fun overVerdict() = snapshot(paidPaise = 500_000, cityAveragePaise = 300_000)
    private fun lowConfidenceVerdict() = snapshot(300_000, 300_000, sampleSize = 2)

    private fun compute(
        entries: List<ServiceLogEntry> = emptyList(),
        documents: List<Document> = emptyList(),
        readings: List<OdometerReading> = emptyList(),
    ) = HealthScoreCalculator.compute(today, entries, documents, readings)

    private fun points(kind: HealthFactorKind, score: com.hopcape.odo.core.domain.health.model.HealthScore) =
        score.factorFor(kind)?.earned

    // --- the whole score ---

    @Test
    fun aCarAddedToday_scoresNothingAndReadsPoor() {
        // Nothing logged, nothing uploaded, one baseline reading: nothing is proven yet.
        val score = compute(readings = listOf(reading(today, 45_000)))

        assertEquals(0, score.total)
        assertEquals(HealthBand.POOR, score.band)
        assertEquals(HealthFactorKind.MAINTENANCE, score.biggestGap?.kind)
    }

    @Test
    fun aFullyDocumentedWellServicedCar_scoresOneHundred() {
        val score = compute(
            entries = listOf(
                entry("1", LocalDate(2026, 2, 10), km = 38_000, verified = true, fairness = fairVerdict()),
                entry("2", LocalDate(2026, 6, 20), km = 43_000, verified = true, fairness = fairVerdict()),
                entry("3", LocalDate(2026, 7, 25), km = 44_500, verified = true, fairness = fairVerdict()),
            ),
            documents = listOf(
                document(DocumentType.INSURANCE, LocalDate(2027, 3, 1)),
                document(DocumentType.PUC, LocalDate(2026, 12, 1)),
                document(DocumentType.RC, expiresOn = null),
            ),
            readings = listOf(
                reading(LocalDate(2026, 1, 1), 36_000),
                reading(LocalDate(2026, 7, 25), 44_500, logId = "3"),
            ),
        )

        assertEquals(100, score.total)
        assertEquals(HealthBand.EXCELLENT, score.band)
    }

    @Test
    fun theSameInputAlwaysGivesTheSameScore() {
        val entries = listOf(entry("1", LocalDate(2026, 6, 20), km = 43_000, verified = true))
        val documents = listOf(document(DocumentType.PUC, LocalDate(2026, 12, 1)))
        val readings = listOf(reading(LocalDate(2026, 1, 1), 36_000), reading(LocalDate(2026, 6, 20), 43_000))

        assertEquals(
            compute(entries, documents, readings),
            compute(entries, documents, readings),
        )
    }

    // --- maintenance (35) ---

    @Test
    fun maintenance_isZeroWithNothingLogged() {
        assertEquals(0, points(HealthFactorKind.MAINTENANCE, compute()))
    }

    @Test
    fun maintenance_isFullWhenServicedRecentlyAndRegularly() {
        val score = compute(
            entries = listOf(
                entry("1", LocalDate(2026, 1, 15), km = 36_000),
                entry("2", LocalDate(2026, 6, 20), km = 43_000),
            ),
            readings = listOf(reading(LocalDate(2026, 6, 20), 43_000)),
        )

        assertEquals(35, points(HealthFactorKind.MAINTENANCE, score))
    }

    @Test
    fun maintenance_losesCadencePointsWhenOnlyOneServiceThisYear() {
        val score = compute(
            entries = listOf(entry("1", LocalDate(2026, 6, 20), km = 43_000)),
            readings = listOf(reading(LocalDate(2026, 6, 20), 43_000)),
        )

        // On time (20) + half the cadence points (7).
        assertEquals(27, points(HealthFactorKind.MAINTENANCE, score))
    }

    @Test
    fun maintenance_dropsAsTheServiceGoesFurtherOverdue() {
        fun servicedOn(date: LocalDate) = compute(entries = listOf(entry("1", date, km = 40_000)))

        // 6-month interval: due 1 Aug, mildly overdue, then long overdue. Nothing was
        // logged in the last twelve months in the last two cases, so cadence is 0 too.
        assertEquals(16 + 7, points(HealthFactorKind.MAINTENANCE, servicedOn(LocalDate(2026, 2, 1))))
        assertEquals(10 + 7, points(HealthFactorKind.MAINTENANCE, servicedOn(LocalDate(2026, 1, 5))))
        assertEquals(5 + 7, points(HealthFactorKind.MAINTENANCE, servicedOn(LocalDate(2025, 11, 20))))
        assertEquals(0, points(HealthFactorKind.MAINTENANCE, servicedOn(LocalDate(2024, 6, 1))))
    }

    @Test
    fun maintenance_countsDistanceAgainstTheIntervalToo() {
        // Serviced last month, so nothing is overdue on time. Distance is what runs out.
        fun drivenTo(km: Int) = compute(
            entries = listOf(entry("1", LocalDate(2026, 7, 1), km = 40_000)),
            readings = listOf(
                reading(LocalDate(2026, 7, 1), 40_000, logId = "1"),
                reading(LocalDate(2026, 7, 30), km),
            ),
        )

        // 9,000 km on a 10,000 km interval: inside the notice window, not past it.
        assertEquals(16 + 7, points(HealthFactorKind.MAINTENANCE, drivenTo(49_000)))
        // 1,000 km past it, then 4,000 km past it, then far enough past to score nothing.
        assertEquals(10 + 7, points(HealthFactorKind.MAINTENANCE, drivenTo(51_000)))
        assertEquals(5 + 7, points(HealthFactorKind.MAINTENANCE, drivenTo(54_000)))
        assertEquals(0 + 7, points(HealthFactorKind.MAINTENANCE, drivenTo(56_000)))
    }

    // --- documentation (30) ---

    @Test
    fun documentation_isFullWithInsurancePucAndRcInForce() {
        val score = compute(
            documents = listOf(
                document(DocumentType.INSURANCE, LocalDate(2027, 3, 1)),
                document(DocumentType.PUC, LocalDate(2026, 12, 1)),
                document(DocumentType.RC, expiresOn = null),
            ),
        )

        assertEquals(30, points(HealthFactorKind.DOCUMENTATION, score))
    }

    @Test
    fun documentation_halvesAPaperInsideItsRenewalWindow() {
        val score = compute(
            documents = listOf(
                document(DocumentType.INSURANCE, LocalDate(2026, 8, 20)),
                document(DocumentType.PUC, LocalDate(2026, 12, 1)),
                document(DocumentType.RC, expiresOn = null),
            ),
        )

        assertEquals(24, points(HealthFactorKind.DOCUMENTATION, score))
    }

    @Test
    fun documentation_scoresNothingForALapsedPaper() {
        val score = compute(
            documents = listOf(
                document(DocumentType.INSURANCE, LocalDate(2026, 5, 1)),
                document(DocumentType.PUC, LocalDate(2026, 12, 1)),
                document(DocumentType.RC, expiresOn = null),
            ),
        )

        assertEquals(18, points(HealthFactorKind.DOCUMENTATION, score))
    }

    @Test
    fun documentation_readsTheRenewedPolicyNotTheExpiredOneBesideIt() {
        val score = compute(
            documents = listOf(
                document(DocumentType.INSURANCE, LocalDate(2026, 5, 1), id = "old"),
                document(DocumentType.INSURANCE, LocalDate(2027, 5, 1), id = "new"),
            ),
        )

        assertEquals(12, points(HealthFactorKind.DOCUMENTATION, score))
    }

    @Test
    fun documentation_ignoresPapersThatSayNothingAboutTheCar() {
        val score = compute(
            documents = listOf(
                document(DocumentType.LICENCE, LocalDate(2030, 1, 1)),
                document(DocumentType.LOAN, expiresOn = null),
            ),
        )

        assertEquals(0, points(HealthFactorKind.DOCUMENTATION, score))
    }

    // --- cost efficiency (20) ---

    @Test
    fun cost_isZeroWhenNoBillWasEverChecked() {
        val score = compute(entries = listOf(entry("1", LocalDate(2026, 6, 1), km = 43_000)))

        assertEquals(0, points(HealthFactorKind.COST_EFFICIENCY, score))
    }

    @Test
    fun cost_isFullWhenEveryEntryWasCheckedAndCameBackFair() {
        val score = compute(
            entries = List(3) {
                entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it, fairness = fairVerdict())
            },
        )

        assertEquals(20, points(HealthFactorKind.COST_EFFICIENCY, score))
    }

    @Test
    fun cost_doesNotGiveFullMarksForASingleFairBill() {
        val score = compute(
            entries = listOf(entry("1", LocalDate(2026, 6, 1), km = 43_000, fairness = fairVerdict())),
        )

        // One of the three entries a record needs before this factor is worth full marks.
        assertEquals(6, points(HealthFactorKind.COST_EFFICIENCY, score))
    }

    @Test
    fun cost_earnsNothingForAnOverchargedEntry() {
        val score = compute(
            entries = List(3) {
                entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it, fairness = overVerdict())
            },
        )

        assertEquals(0, points(HealthFactorKind.COST_EFFICIENCY, score))
    }

    @Test
    fun cost_treatsAThinlySampledVerdictAsUncheckedNotAsAnOvercharge() {
        val thin = compute(
            entries = List(3) {
                entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it, fairness = lowConfidenceVerdict())
            },
        )
        val unchecked = compute(
            entries = List(3) { entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it) },
        )

        assertEquals(
            points(HealthFactorKind.COST_EFFICIENCY, unchecked),
            points(HealthFactorKind.COST_EFFICIENCY, thin),
        )
    }

    // --- history (15) ---

    @Test
    fun history_isFullWithBillsAttachedAndACleanTimeline() {
        val score = compute(
            entries = List(3) {
                entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it, verified = true)
            },
            readings = listOf(
                reading(LocalDate(2026, 1, 1), 36_000),
                reading(LocalDate(2026, 6, 1), 43_000),
            ),
        )

        assertEquals(15, points(HealthFactorKind.HISTORY, score))
    }

    @Test
    fun history_losesTheConsistencyPointsWhenTheOdometerGoesBackwards() {
        val score = compute(
            entries = List(3) {
                entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it, verified = true)
            },
            readings = listOf(
                reading(LocalDate(2026, 1, 1), 44_000),
                reading(LocalDate(2026, 6, 1), 43_000),
            ),
        )

        assertEquals(10, points(HealthFactorKind.HISTORY, score))
    }

    @Test
    fun history_provesNothingFromASingleReading() {
        val score = compute(
            entries = List(3) {
                entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it, verified = true)
            },
            readings = listOf(reading(LocalDate(2026, 6, 1), 43_000)),
        )

        assertEquals(10, points(HealthFactorKind.HISTORY, score))
    }

    @Test
    fun history_scoresSelfReportedEntriesLowerThanBillBackedOnes() {
        val readings = listOf(
            reading(LocalDate(2026, 1, 1), 36_000),
            reading(LocalDate(2026, 6, 1), 43_000),
        )
        val selfReported = compute(
            entries = List(3) { entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it) },
            readings = readings,
        )
        val oneBill = compute(
            entries = List(3) {
                entry("$it", LocalDate(2026, 6, 1), km = 43_000 + it, verified = it == 0)
            },
            readings = readings,
        )

        assertEquals(5, points(HealthFactorKind.HISTORY, selfReported))
        assertEquals(8, points(HealthFactorKind.HISTORY, oneBill))
    }
}
