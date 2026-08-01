package com.hopcape.odo.core.domain.alerts.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AttentionPickerTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val today = LocalDate(2026, 8, 1)

    /* ------------------------- fixtures ------------------------- */

    private fun document(
        id: String,
        type: DocumentType,
        expiresOn: LocalDate?,
    ) = Document.reconstitute(
        id = DocumentId(id),
        ownerId = ownerId,
        carId = carId,
        type = type,
        storagePath = "/vault/$id.pdf",
        source = DocumentSource.UPLOADED,
        addedOn = LocalDate(2025, 1, 1),
        expiresOn = expiresOn,
    )

    private fun entry(date: LocalDate, km: Int) = ServiceLogEntry.reconstitute(
        id = ServiceLogId("log-${date}-$km"),
        carId = carId,
        ownerId = ownerId,
        serviceDate = date,
        odometerKm = km,
        totalAmountPaise = 300_000,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
    )

    private fun reading(date: LocalDate, km: Int) = OdometerReading(
        logId = null,
        date = date,
        odometer = Distance.of(km).getOrElse { error("test fixture km=$km") },
    )

    /** A service two months ago at 50,000 km — comfortably inside the interval. */
    private fun freshService() = listOf(entry(LocalDate(2026, 6, 1), 50_000))

    private fun freshReadings() = listOf(reading(LocalDate(2026, 7, 20), 51_000))

    /* ------------------------- nothing due ------------------------- */

    @Test
    fun `nothing due returns null`() {
        val attention = AttentionPicker.pick(
            documents = listOf(document("ins", DocumentType.INSURANCE, LocalDate(2027, 3, 1))),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )

        assertNull(attention)
    }

    @Test
    fun `a car with no history at all raises nothing`() {
        val attention = AttentionPicker.pick(
            documents = emptyList(),
            entries = emptyList(),
            readings = emptyList(),
            today = today,
        )

        assertNull(attention)
    }

    /* ------------------------- documents ------------------------- */

    @Test
    fun `a lapsed paper outranks everything else`() {
        val attention = AttentionPicker.pick(
            documents = listOf(document("puc", DocumentType.PUC, LocalDate(2026, 7, 25))),
            // Overdue on time as well, so the lapsed paper has to beat it.
            entries = listOf(entry(LocalDate(2025, 1, 1), 40_000)),
            readings = listOf(reading(LocalDate(2026, 7, 20), 60_000)),
            today = today,
        )

        val lapsed = assertIs<CarAttention.DocumentLapsed>(attention)
        assertEquals(DocumentType.PUC, lapsed.type)
        assertEquals(7, lapsed.daysAgo)
        assertEquals(LocalDate(2026, 7, 25), lapsed.since)
    }

    @Test
    fun `insurance is raised before a puc that lapsed earlier`() {
        val attention = AttentionPicker.pick(
            documents = listOf(
                document("puc", DocumentType.PUC, LocalDate(2026, 1, 1)),
                document("ins", DocumentType.INSURANCE, LocalDate(2026, 7, 30)),
            ),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )

        assertEquals(DocumentType.INSURANCE, assertIs<CarAttention.DocumentLapsed>(attention).type)
    }

    /** A renewal is filed as a new document, so last year's policy must stay quiet. */
    @Test
    fun `a renewed paper silences the lapsed one it replaced`() {
        val attention = AttentionPicker.pick(
            documents = listOf(
                document("ins-2025", DocumentType.INSURANCE, LocalDate(2026, 3, 1)),
                document("ins-2026", DocumentType.INSURANCE, LocalDate(2027, 3, 1)),
            ),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )

        assertNull(attention)
    }

    /** An RC never expires and a loan letter has no renewal, so neither is chased. */
    @Test
    fun `papers Odo does not chase raise nothing even when dated in the past`() {
        val attention = AttentionPicker.pick(
            documents = listOf(
                document("rc", DocumentType.RC, LocalDate(2026, 1, 1)),
                document("loan", DocumentType.LOAN, LocalDate(2026, 2, 1)),
                document("other", DocumentType.OTHER, LocalDate(2026, 3, 1)),
            ),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )

        assertNull(attention)
    }

    @Test
    fun `a paper inside its renewal window is raised as expiring`() {
        val attention = AttentionPicker.pick(
            documents = listOf(document("ins", DocumentType.INSURANCE, LocalDate(2026, 8, 20))),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )

        val expiring = assertIs<CarAttention.DocumentExpiring>(attention)
        assertEquals(19, expiring.daysLeft)
        assertEquals(LocalDate(2026, 8, 20), expiring.until)
    }

    /**
     * Each type carries its own window: a PUC 20 days out is not yet in one (its longest
     * lead is 15 days), while an insurance at the same distance is.
     */
    @Test
    fun `the renewal window follows the paper's own lead times`() {
        val puc = AttentionPicker.pick(
            documents = listOf(document("puc", DocumentType.PUC, LocalDate(2026, 8, 21))),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )
        val insurance = AttentionPicker.pick(
            documents = listOf(document("ins", DocumentType.INSURANCE, LocalDate(2026, 8, 21))),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )

        assertNull(puc)
        assertIs<CarAttention.DocumentExpiring>(insurance)
    }

    @Test
    fun `the paper expiring soonest wins among several`() {
        val attention = AttentionPicker.pick(
            documents = listOf(
                document("ins", DocumentType.INSURANCE, LocalDate(2026, 8, 25)),
                document("puc", DocumentType.PUC, LocalDate(2026, 8, 5)),
            ),
            entries = freshService(),
            readings = freshReadings(),
            today = today,
        )

        assertEquals(DocumentType.PUC, assertIs<CarAttention.DocumentExpiring>(attention).type)
    }

    /* ------------------------- service ------------------------- */

    @Test
    fun `an overdue service outranks a paper that is merely expiring`() {
        val attention = AttentionPicker.pick(
            documents = listOf(document("ins", DocumentType.INSURANCE, LocalDate(2026, 8, 20))),
            entries = listOf(entry(LocalDate(2025, 6, 1), 40_000)),
            readings = listOf(reading(LocalDate(2026, 7, 20), 45_000)),
            today = today,
        )

        val overdue = assertIs<CarAttention.ServiceOverdue>(attention)
        assertEquals(0, overdue.kmOverdue)
    }

    @Test
    fun `a service approaching its interval is the last thing raised`() {
        val attention = AttentionPicker.pick(
            documents = emptyList(),
            entries = listOf(entry(LocalDate(2026, 2, 10), 40_000)),
            readings = listOf(reading(LocalDate(2026, 7, 20), 49_500)),
            today = today,
        )

        val due = assertIs<CarAttention.ServiceDue>(attention)
        assertEquals(500, due.kmLeft)
    }

    /** Never serviced is the setup checklist's business, not an overdue deadline. */
    @Test
    fun `a car that was never serviced raises no service attention`() {
        val attention = AttentionPicker.pick(
            documents = emptyList(),
            entries = emptyList(),
            readings = listOf(reading(LocalDate(2026, 7, 20), 80_000)),
            today = today,
        )

        assertNull(attention)
    }
}
