package com.hopcape.odo.core.domain.record.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.record.model.RecordStatus
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ServiceRecordBuilderTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val kolkata = TimeZone.of("Asia/Kolkata")
    private val today = LocalDate(2026, 8, 12)

    /* ------------------------- fixtures ------------------------- */

    private fun amount(paise: Long) = Amount.of(paise).getOrElse { error("test fixture paise=$paise") }

    private fun car(
        addedOn: LocalDate? = LocalDate(2020, 8, 6),
        purchaseYear: Int? = 2020,
        nickname: String? = null,
    ) = Car.reconstitute(
        id = carId,
        ownerId = ownerId,
        make = "Maruti",
        model = "Swift",
        variant = "VXI",
        year = 2020,
        fuelType = FuelType.PETROL,
        registrationNumber = "MH12AB1234",
        odometerKm = 54_120,
        purchaseYear = purchaseYear,
        nickname = nickname,
        isPrimary = true,
        addedOn = addedOn,
    )

    private fun owner(name: String? = "Rahul Deshmukh") = OwnerProfile.reconstitute(
        id = ownerId,
        name = name,
        onboardingCompletedAt = null,
    )

    private fun entry(
        id: String,
        date: LocalDate,
        km: Int = 50_000,
        paise: Long = 320_000,
        verified: Boolean = true,
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = date,
        odometerKm = km,
        totalAmountPaise = paise,
        workshopName = "Sharma Motors",
        notes = "Oil change + oil filter",
        source = LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
        categories = emptySet(),
        lineItems = emptyList(),
        fairness = null,
    )

    private fun document(
        type: DocumentType,
        addedOn: LocalDate? = LocalDate(2026, 6, 1),
        expiresOn: LocalDate? = LocalDate(2027, 7, 3),
    ) = Document.reconstitute(
        id = DocumentId("doc-$type-$addedOn"),
        ownerId = ownerId,
        carId = carId,
        type = type,
        storagePath = "documents/doc-$type.pdf",
        source = DocumentSource.UPLOADED,
        addedOn = addedOn,
        issuedOn = null,
        expiresOn = expiresOn,
    )

    private fun snapshot(at: String, total: Int) = HealthSnapshot(
        id = HealthSnapshotId("snap-$at"),
        carId = carId,
        ownerId = ownerId,
        score = scoreOf(total),
        computedAt = Instant.parse(at),
        algoVersion = "rule-v1",
    )

    private fun scoreOf(total: Int): HealthScore {
        var left = total
        return HealthScore(
            factors = HealthFactorKind.entries.map { kind ->
                val earned = minOf(left, kind.weight)
                left -= earned
                HealthFactor.of(kind, earned)
            },
        )
    }

    private fun build(
        car: Car? = car(),
        owner: OwnerProfile? = owner(),
        entries: List<ServiceLogEntry> = emptyList(),
        documents: List<Document> = emptyList(),
        scores: List<HealthSnapshot> = emptyList(),
    ) = ServiceRecordBuilder.build(
        car = car,
        owner = owner,
        entries = entries,
        documents = documents,
        scores = scores,
        today = today,
        zone = kolkata,
    )

    /* ------------------------- rows ------------------------- */

    @Test
    fun `rows keep the activity feed's order newest first`() {
        val record = build(
            entries = listOf(
                entry("older", LocalDate(2025, 2, 11), km = 31_400),
                entry("newer", LocalDate(2026, 7, 12), km = 54_000),
            ),
        )

        assertEquals(
            listOf(LocalDate(2026, 7, 12), LocalDate(2025, 2, 11), LocalDate(2020, 8, 6)),
            record.rows.map { it.date },
            "the record must print newest first, ending on the car's opening milestone",
        )
    }

    @Test
    fun `score changes are not printed`() {
        val record = build(
            entries = listOf(entry("a", LocalDate(2026, 7, 12))),
            scores = listOf(
                snapshot("2026-07-01T10:00:00Z", total = 60),
                snapshot("2026-07-02T10:00:00Z", total = 74),
            ),
        )

        assertTrue(
            record.rows.none { it.event is ActivityEvent.ScoreChanged },
            "a buyer reading a service history has no use for the app's own number moving",
        )
    }

    @Test
    fun `a document row is verified and the opening milestone is not`() {
        val record = build(documents = listOf(document(DocumentType.INSURANCE)))

        val documentRow = record.rows.first { it.event is ActivityEvent.DocumentFiled }
        val milestone = record.rows.first { it.event is ActivityEvent.CarAdded }

        assertEquals(RecordStatus.VERIFIED, documentRow.status, "a filed document is a file on disk")
        assertEquals(RecordStatus.SELF_REPORTED, milestone.status, "nobody proved the car was bought")
    }

    @Test
    fun `a service without a bill is self-reported`() {
        val record = build(
            entries = listOf(
                entry("billed", LocalDate(2026, 7, 12), verified = true),
                entry("unbilled", LocalDate(2026, 6, 4), verified = false),
            ),
        )

        assertEquals(RecordStatus.VERIFIED, record.rows.first { it.date == LocalDate(2026, 7, 12) }.status)
        assertEquals(RecordStatus.SELF_REPORTED, record.rows.first { it.date == LocalDate(2026, 6, 4) }.status)
    }

    /* ------------------------- counts and total ------------------------- */

    @Test
    fun `counts describe the printed rows not the stored entries`() {
        val record = build(
            entries = listOf(
                entry("a", LocalDate(2026, 7, 12), verified = true),
                entry("b", LocalDate(2026, 6, 4), verified = false),
            ),
            documents = listOf(document(DocumentType.INSURANCE)),
            scores = listOf(
                snapshot("2026-07-01T10:00:00Z", total = 60),
                snapshot("2026-07-02T10:00:00Z", total = 74),
            ),
        )

        // Two services, one document, one milestone. The score move is not a row.
        assertEquals(4, record.entryCount)
        assertEquals(2, record.verifiedCount, "the billed service and the filed document")
    }

    @Test
    fun `the total sums only the lines that carry an amount`() {
        val record = build(
            entries = listOf(
                entry("a", LocalDate(2026, 7, 12), paise = 320_000),
                entry("b", LocalDate(2026, 6, 4), paise = 240_000),
            ),
            documents = listOf(document(DocumentType.INSURANCE)),
        )

        assertEquals(
            amount(560_000),
            record.total,
            "a filed document has no premium stored against it, so it adds nothing",
        )
    }

    @Test
    fun `the health score is the most recent snapshot`() {
        val record = build(
            scores = listOf(
                snapshot("2026-07-02T10:00:00Z", total = 74),
                snapshot("2026-07-01T10:00:00Z", total = 60),
            ),
        )

        assertEquals(74, record.healthScore)
    }

    @Test
    fun `a car that was never scored has no score`() {
        assertNull(build().healthScore)
    }

    /* ------------------------- record span ------------------------- */

    @Test
    fun `the record starts at its oldest line even one logged before the car was added`() {
        val record = build(
            car = car(addedOn = LocalDate(2026, 1, 5)),
            entries = listOf(entry("backdated", LocalDate(2020, 8, 6), km = 0)),
        )

        assertEquals(
            LocalDate(2020, 8, 6),
            record.recordSince,
            "history is logged backwards, so the oldest entry can predate the install",
        )
    }

    /* ------------------------- ownership ------------------------- */

    @Test
    fun `ownership reads the owner's name and the year they bought the car`() {
        val ownership = build().ownership

        assertEquals("Rahul Deshmukh", ownership.ownerName)
        assertEquals(2020, ownership.ownedSince)
    }

    @Test
    fun `an odometer that only counts up is consistent`() {
        val record = build(
            entries = listOf(
                entry("a", LocalDate(2025, 2, 11), km = 31_400),
                entry("b", LocalDate(2026, 7, 12), km = 54_000),
            ),
        )

        assertTrue(record.ownership.odometerConsistent)
    }

    @Test
    fun `a reading below an earlier one is reported as inconsistent`() {
        val record = build(
            entries = listOf(
                entry("a", LocalDate(2025, 2, 11), km = 54_000),
                entry("b", LocalDate(2026, 7, 12), km = 31_400),
            ),
        )

        assertFalse(
            record.ownership.odometerConsistent,
            "a rollback is the single thing a buyer is reading this document to find",
        )
    }

    /* ------------------------- documents on file ------------------------- */

    @Test
    fun `documents on file print in a fixed order with their validity`() {
        val record = build(
            documents = listOf(
                document(DocumentType.RC, expiresOn = null),
                document(DocumentType.INSURANCE, expiresOn = LocalDate(2027, 7, 3)),
            ),
        )

        assertEquals(
            listOf(DocumentType.INSURANCE, DocumentType.RC),
            record.documents.map { it.type },
            "the order is the document's, not the vault's",
        )
        assertEquals(DocumentValidity.NoExpiry, record.documents.last().validity)
        assertTrue(record.documents.first().validity is DocumentValidity.Valid)
    }

    @Test
    fun `a licence is never printed among the car's papers`() {
        val record = build(documents = listOf(document(DocumentType.LICENCE)))

        assertTrue(
            record.documents.isEmpty(),
            "a licence belongs to the owner; a record handed to a buyer must not carry it",
        )
    }

    @Test
    fun `a type with nothing filed is absent rather than empty`() {
        val record = build(documents = listOf(document(DocumentType.INSURANCE)))

        assertEquals(listOf(DocumentType.INSURANCE), record.documents.map { it.type })
    }

    /* ------------------------- degenerate input ------------------------- */

    @Test
    fun `a car with no history still produces a record`() {
        val record = build()

        assertEquals(1, record.entryCount, "the opening milestone is the whole record")
        assertEquals(Amount.ZERO, record.total)
        assertTrue(record.ownership.odometerConsistent, "nothing logged contradicts nothing logged")
        assertEquals("Maruti Swift VXI", record.carName)
    }

    @Test
    fun `a record with no car at all is empty rather than broken`() {
        val record = build(car = null, owner = null)

        assertTrue(record.isEmpty)
        assertEquals("", record.carName)
        assertNull(record.healthScore)
        assertNull(record.recordSince)
        assertNull(record.ownership.ownerName)
    }

    @Test
    fun `the nickname names the document but the model still names the header`() {
        val record = build(car = car(nickname = "Daily"))

        assertEquals("Daily", record.carName)
        assertEquals("Maruti Swift VXI", record.modelName)
    }

    @Test
    fun `the document is dated the day it was produced`() {
        assertEquals(today, build().issuedOn)
    }
}
