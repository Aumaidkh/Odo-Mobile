package com.hopcape.odo.core.domain.activity.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ActivityFeedBuilderTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val delhi = TimeZone.of("Asia/Kolkata")

    /* ------------------------- fixtures ------------------------- */

    private fun amount(paise: Long) = Amount.of(paise).getOrElse { error("test fixture paise=$paise") }

    private fun car(addedOn: LocalDate? = LocalDate(2026, 1, 5), nickname: String? = "Daily") =
        Car.reconstitute(
            id = carId,
            ownerId = ownerId,
            make = "Maruti Suzuki",
            model = "Swift",
            variant = "VXI",
            year = 2020,
            fuelType = FuelType.PETROL,
            registrationNumber = null,
            odometerKm = 54_000,
            purchaseYear = null,
            nickname = nickname,
            isPrimary = true,
            addedOn = addedOn,
        )

    private fun entry(
        id: String,
        date: LocalDate,
        km: Int = 50_000,
        paise: Long = 300_000,
        verified: Boolean = false,
        fairness: FairnessSnapshot? = null,
        workshop: String? = "Sharma Motors",
        notes: String? = "Oil change + filter",
        categories: Set<ServiceCategory> = emptySet(),
        lineItems: List<ServiceLogLineItem> = emptyList(),
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = date,
        odometerKm = km,
        totalAmountPaise = paise,
        workshopName = workshop,
        notes = notes,
        source = LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
        categories = categories,
        lineItems = lineItems,
        fairness = fairness,
    )

    private fun document(
        type: DocumentType,
        addedOn: LocalDate?,
        expiresOn: LocalDate? = LocalDate(2027, 3, 31),
        issuedOn: LocalDate? = null,
        id: String = "doc-$type-$addedOn",
    ) = Document.reconstitute(
        id = DocumentId(id),
        ownerId = ownerId,
        carId = carId,
        type = type,
        storagePath = "documents/$id.pdf",
        source = DocumentSource.UPLOADED,
        addedOn = addedOn,
        issuedOn = issuedOn,
        expiresOn = expiresOn,
    )

    private fun snapshot(
        id: String,
        at: String,
        total: Int,
        algoVersion: String = "rule-v1",
    ) = HealthSnapshot(
        id = HealthSnapshotId(id),
        carId = carId,
        ownerId = ownerId,
        score = scoreOf(total),
        computedAt = Instant.parse(at),
        algoVersion = algoVersion,
    )

    /**
     * A score adding up to [total]. Poured into the factors in order, because each one
     * clamps at its own weight and a single factor cannot carry a whole score.
     */
    private fun scoreOf(total: Int): HealthScore {
        var left = total
        val factors = HealthFactorKind.entries.map { kind ->
            val earned = minOf(left, kind.weight)
            left -= earned
            HealthFactor.of(kind, earned)
        }
        return HealthScore(factors = factors)
    }

    /** An overcharged verdict, built the way the fairness check builds one. */
    private fun overcharged(byPaise: Long): FairnessSnapshot {
        val estimate = FairnessEstimate(
            category = ServiceCategory.GENERAL_SERVICE,
            city = "Pune",
            cityAverage = amount(400_000),
            sampleSize = 30,
        )
        return FairnessSnapshot(
            report = FairnessReport(
                city = "Pune",
                items = listOf(
                    FairnessReportItem(
                        label = "General service",
                        category = ServiceCategory.GENERAL_SERVICE,
                        amount = amount(400_000 + byPaise),
                        estimate = estimate,
                        verdict = FairnessVerdict.Over(by = amount(byPaise)),
                    ),
                ),
            ),
            checkedAt = Instant.parse("2026-07-08T10:00:00Z"),
        )
    }

    private fun build(
        car: Car? = car(),
        entries: List<ServiceLogEntry> = emptyList(),
        documents: List<Document> = emptyList(),
        scores: List<HealthSnapshot> = emptyList(),
    ) = ActivityFeedBuilder.build(car, entries, documents, scores, delhi)

    /* ------------------------- ordering ------------------------- */

    @Test
    fun feed_isNewestFirst() {
        val feed = build(
            entries = listOf(
                entry("old", LocalDate(2026, 6, 4)),
                entry("new", LocalDate(2026, 7, 12)),
            ),
            documents = listOf(document(DocumentType.PUC, addedOn = LocalDate(2026, 6, 20))),
        )

        assertEquals(
            listOf(LocalDate(2026, 7, 12), LocalDate(2026, 6, 20), LocalDate(2026, 6, 4), LocalDate(2026, 1, 5)),
            feed.map { it.date },
        )
    }

    @Test
    fun eventsOnOneDay_areRankedSoTheOrderNeverReshuffles() {
        val day = LocalDate(2026, 7, 8)
        val feed = build(
            car = car(addedOn = day),
            entries = listOf(entry("s1", day)),
            documents = listOf(document(DocumentType.PUC, addedOn = day)),
            scores = listOf(
                snapshot("before", "2026-07-07T10:00:00Z", total = 70),
                snapshot("after", "2026-07-08T10:00:00Z", total = 74),
            ),
        )

        // Service, then the score move it caused, then the document, then the milestone.
        assertTrue(feed[0] is ActivityEvent.Service, "was ${feed[0]}")
        assertTrue(feed[1] is ActivityEvent.ScoreChanged, "was ${feed[1]}")
        assertTrue(feed[2] is ActivityEvent.DocumentFiled, "was ${feed[2]}")
        assertTrue(feed[3] is ActivityEvent.CarAdded, "was ${feed[3]}")
    }

    @Test
    fun feed_isTheSameForTheSameInputs() {
        val entries = listOf(entry("a", LocalDate(2026, 7, 12)), entry("b", LocalDate(2026, 7, 12)))

        assertEquals(build(entries = entries), build(entries = entries))
    }

    /* ------------------------- services ------------------------- */

    @Test
    fun service_carriesTheStoredVerdictAndDerivedVerification() {
        val feed = build(
            car = null,
            entries = listOf(
                entry("flagged", LocalDate(2026, 7, 8), verified = true, fairness = overcharged(70_000)),
                entry("plain", LocalDate(2026, 7, 7)),
            ),
        )

        val flagged = feed[0] as ActivityEvent.Service
        assertEquals(amount(70_000), flagged.overchargedBy)
        assertEquals(VerificationStatus.VERIFIED, flagged.verification)

        val plain = feed[1] as ActivityEvent.Service
        assertNull(plain.overchargedBy)
        assertEquals(VerificationStatus.SELF_REPORTED, plain.verification)
    }

    @Test
    fun service_describesTheWorkTheWayTheEntryDoes() {
        val feed = build(
            car = null,
            entries = listOf(
                entry(
                    "itemised",
                    LocalDate(2026, 7, 12),
                    lineItems = listOf(
                        ServiceLogLineItem(label = "Engine oil", category = ServiceCategory.OIL_CHANGE, amount = amount(190_000)),
                    ),
                ),
                entry("tagged", LocalDate(2026, 7, 11), notes = null, categories = setOf(ServiceCategory.BRAKES)),
                entry("bare", LocalDate(2026, 7, 10), notes = null),
            ),
        )

        assertEquals(WorkDone.Described(listOf("Engine oil")), (feed[0] as ActivityEvent.Service).workDone)
        assertEquals(WorkDone.Tagged(listOf(ServiceCategory.BRAKES)), (feed[1] as ActivityEvent.Service).workDone)
        assertEquals(WorkDone.Unspecified, (feed[2] as ActivityEvent.Service).workDone)
    }

    /* ------------------------- documents ------------------------- */

    @Test
    fun firstDocumentOfATypeIsNotARenewal_laterOnesAre() {
        val feed = build(
            car = null,
            documents = listOf(
                // Deliberately out of order: the rule is about dates, not list position.
                document(DocumentType.INSURANCE, addedOn = LocalDate(2026, 6, 1), id = "ins-2026"),
                document(DocumentType.INSURANCE, addedOn = LocalDate(2025, 6, 1), id = "ins-2025"),
                document(DocumentType.PUC, addedOn = LocalDate(2026, 6, 2), id = "puc"),
            ),
        )

        val filings = feed.filterIsInstance<ActivityEvent.DocumentFiled>()
        assertEquals(
            listOf(
                Triple(DocumentType.INSURANCE, true, LocalDate(2026, 6, 1)),
                Triple(DocumentType.PUC, false, LocalDate(2026, 6, 2)),
                Triple(DocumentType.INSURANCE, false, LocalDate(2025, 6, 1)),
            ).sortedByDescending { it.third },
            filings.map { Triple(it.document, it.isRenewal, it.date) },
        )
    }

    @Test
    fun document_isDatedByWhenItWasFiledNotWhenItWasIssued() {
        val feed = build(
            car = null,
            documents = listOf(
                document(
                    DocumentType.INSURANCE,
                    addedOn = LocalDate(2026, 7, 30),
                    issuedOn = LocalDate(2026, 4, 1),
                ),
            ),
        )

        assertEquals(LocalDate(2026, 7, 30), feed.single().date)
    }

    @Test
    fun document_withNoDateAtAllIsSkipped() {
        // Only an unstored document can be in this state; it has no place on a dated feed.
        val feed = build(
            car = null,
            documents = listOf(document(DocumentType.RC, addedOn = null, issuedOn = null)),
        )

        assertEquals(emptyList(), feed)
    }

    @Test
    fun twoPapersOfOneTypeFiledTheSameDayAreTwoEvents() {
        val feed = build(
            car = null,
            documents = listOf(
                document(DocumentType.INSURANCE, addedOn = LocalDate(2026, 6, 1), id = "ins-a"),
                document(DocumentType.INSURANCE, addedOn = LocalDate(2026, 6, 1), id = "ins-b"),
            ),
        )

        // Each carries its own document, so a screen keying rows by identity can tell them
        // apart — the type and the date alone cannot.
        assertEquals(
            listOf(DocumentId("ins-a"), DocumentId("ins-b")),
            feed.filterIsInstance<ActivityEvent.DocumentFiled>().map { it.id }.sortedBy { it.value },
        )
    }

    @Test
    fun document_carriesItsExpiry() {
        val feed = build(
            car = null,
            documents = listOf(
                document(DocumentType.PUC, addedOn = LocalDate(2026, 6, 2), expiresOn = LocalDate(2026, 11, 30)),
                document(DocumentType.RC, addedOn = LocalDate(2026, 6, 1), expiresOn = null, id = "rc"),
            ),
        )

        val filings = feed.filterIsInstance<ActivityEvent.DocumentFiled>()
        assertEquals(LocalDate(2026, 11, 30), filings[0].validTill)
        assertNull(filings[1].validTill)
    }

    /* ------------------------- score moves ------------------------- */

    @Test
    fun scoreMove_isOneEventPerDayNotOnePerSnapshot() {
        val feed = build(
            car = null,
            scores = listOf(
                snapshot("mon", "2026-07-06T10:00:00Z", total = 70),
                snapshot("tue-1", "2026-07-07T04:00:00Z", total = 71),
                snapshot("tue-2", "2026-07-07T09:00:00Z", total = 73),
                snapshot("tue-3", "2026-07-07T14:00:00Z", total = 76),
            ),
        )

        // Three snapshots on the Tuesday, one row: where the day opened to where it closed.
        val move = feed.single() as ActivityEvent.ScoreChanged
        assertEquals(70, move.from.value)
        assertEquals(76, move.to.value)
        assertEquals(LocalDate(2026, 7, 7), move.date)
    }

    @Test
    fun scoreMove_needsSomethingBeforeItToCompareAgainst() {
        val feed = build(car = null, scores = listOf(snapshot("first", "2026-07-06T10:00:00Z", total = 70)))

        // The first score a car is ever given is not a move.
        assertEquals(emptyList(), feed)
    }

    @Test
    fun scoreMove_isDroppedWhenTheDayNetsOut() {
        val feed = build(
            car = null,
            scores = listOf(
                snapshot("mon", "2026-07-06T10:00:00Z", total = 70),
                snapshot("tue-up", "2026-07-07T04:00:00Z", total = 74),
                snapshot("tue-back", "2026-07-07T18:00:00Z", total = 70),
            ),
        )

        assertEquals(emptyList(), feed)
    }

    @Test
    fun scoreMove_isDroppedAcrossARulesVersionChange() {
        val feed = build(
            car = null,
            scores = listOf(
                snapshot("old-rules", "2026-07-06T10:00:00Z", total = 70, algoVersion = "rule-v1"),
                snapshot("new-rules", "2026-07-07T10:00:00Z", total = 79, algoVersion = "rule-v2"),
            ),
        )

        // The nine points came from the release, not from the car.
        assertEquals(emptyList(), feed)
    }

    @Test
    fun scoreMove_isPlacedOnTheOwnersDayNotTheUtcOne() {
        val feed = build(
            car = null,
            scores = listOf(
                snapshot("before", "2026-07-06T10:00:00Z", total = 70),
                // 20:30 UTC on the 7th is 2am on the 8th in Delhi.
                snapshot("late", "2026-07-07T20:30:00Z", total = 74),
            ),
        )

        assertEquals(LocalDate(2026, 7, 8), feed.single().date)
    }

    @Test
    fun scoreMove_canFall() {
        val feed = build(
            car = null,
            scores = listOf(
                snapshot("before", "2026-07-06T10:00:00Z", total = 74),
                snapshot("after", "2026-07-07T10:00:00Z", total = 68),
            ),
        )

        val move = feed.single() as ActivityEvent.ScoreChanged
        assertEquals(74, move.from.value)
        assertEquals(68, move.to.value)
    }

    /* ------------------------- the milestone ------------------------- */

    @Test
    fun milestone_namesTheCarTheWayEverySurfaceDoes() {
        val feed = build(car = car(nickname = null))

        val milestone = feed.single() as ActivityEvent.CarAdded
        assertEquals("Maruti Suzuki Swift VXI", milestone.carName)
        assertEquals(LocalDate(2026, 1, 5), milestone.date)
    }

    @Test
    fun milestone_isAbsentUntilTheCarHasBeenStored() {
        assertEquals(emptyList(), build(car = car(addedOn = null)))
        assertEquals(emptyList(), build(car = null))
    }
}
