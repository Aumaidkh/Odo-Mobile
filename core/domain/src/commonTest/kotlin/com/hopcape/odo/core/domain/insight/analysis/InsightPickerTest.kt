package com.hopcape.odo.core.domain.insight.analysis

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.model.CostTrend
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class InsightPickerTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    /* ------------------------- fixtures ------------------------- */

    private fun entry(id: String, verified: Boolean) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = LocalDate(2026, 6, 1),
        odometerKm = 50_000,
        totalAmountPaise = 300_000,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
    )

    private fun entries(count: Int, verified: Boolean) =
        (1..count).map { entry("log-$it", verified) }

    private fun document(type: DocumentType) = Document.reconstitute(
        id = DocumentId("doc-$type"),
        ownerId = ownerId,
        carId = carId,
        type = type,
        storagePath = "/vault/$type.pdf",
        source = DocumentSource.UPLOADED,
        addedOn = LocalDate(2026, 1, 1),
        expiresOn = LocalDate(2027, 1, 1),
    )

    /** Every paper the score counts, so the missing-document rule stays out of the way. */
    private fun fullVault() = listOf(
        document(DocumentType.INSURANCE),
        document(DocumentType.PUC),
        document(DocumentType.RC),
    )

    /* ------------------------- nothing to say ------------------------- */

    @Test
    fun `a brand new car with nothing on it yields no insight`() {
        val insight = InsightPicker.pick(
            entries = emptyList(),
            documents = fullVault(),
            costTrend = null,
        )

        assertNull(insight)
    }

    /* ------------------------- resale ready ------------------------- */

    @Test
    fun `a fully verified record of three services reads as resale ready`() {
        val insight = InsightPicker.pick(
            entries = entries(count = 3, verified = true),
            documents = fullVault(),
            costTrend = CostTrend(percentChange = 40),
        )

        assertEquals(3, assertIs<CarInsight.ResaleReady>(insight).serviceCount)
    }

    /** One bill is a receipt, not a history. */
    @Test
    fun `two verified services are not enough to claim resale ready`() {
        val insight = InsightPicker.pick(
            entries = entries(count = 2, verified = true),
            documents = fullVault(),
            costTrend = null,
        )

        assertNull(insight)
    }

    @Test
    fun `one self-reported entry among verified ones breaks resale ready`() {
        val insight = InsightPicker.pick(
            entries = entries(count = 3, verified = true) + entry("unverified", verified = false),
            documents = fullVault(),
            costTrend = null,
        )

        assertNull(insight)
    }

    /* ------------------------- cost trend ------------------------- */

    @Test
    fun `a cost move worth reporting is raised with its sign intact`() {
        val insight = InsightPicker.pick(
            entries = entries(count = 2, verified = true),
            documents = fullVault(),
            costTrend = CostTrend(percentChange = -14),
        )

        assertEquals(-14, assertIs<CarInsight.CostMoved>(insight).percentChange)
    }

    @Test
    fun `a small cost move is noise and says nothing`() {
        val insight = InsightPicker.pick(
            entries = entries(count = 2, verified = true),
            documents = fullVault(),
            costTrend = CostTrend(percentChange = 9),
        )

        assertNull(insight)
    }

    /* ------------------------- bills ------------------------- */

    @Test
    fun `a history with no bills attached is the gap worth naming`() {
        val insight = InsightPicker.pick(
            entries = entries(count = 4, verified = false),
            documents = fullVault(),
            costTrend = CostTrend(percentChange = 2),
        )

        assertEquals(4, assertIs<CarInsight.NoBillsAttached>(insight).serviceCount)
    }

    @Test
    fun `one attached bill is enough to stop asking`() {
        val insight = InsightPicker.pick(
            entries = entries(count = 3, verified = false) + entry("scanned", verified = true),
            documents = fullVault(),
            costTrend = null,
        )

        assertNull(insight)
    }

    /* ------------------------- documents ------------------------- */

    @Test
    fun `the heaviest missing paper is the one asked for`() {
        val insight = InsightPicker.pick(
            entries = emptyList(),
            documents = listOf(document(DocumentType.RC)),
            costTrend = null,
        )

        assertEquals(DocumentType.INSURANCE, assertIs<CarInsight.DocumentMissing>(insight).type)
    }

    /** A lapsed paper is the attention card's line; the insight must not repeat it. */
    @Test
    fun `a lapsed paper still counts as filed and is not asked for again`() {
        val lapsedInsurance = Document.reconstitute(
            id = DocumentId("ins-old"),
            ownerId = ownerId,
            carId = carId,
            type = DocumentType.INSURANCE,
            storagePath = "/vault/ins-old.pdf",
            source = DocumentSource.UPLOADED,
            addedOn = LocalDate(2025, 1, 1),
            expiresOn = LocalDate(2026, 1, 1),
        )

        val insight = InsightPicker.pick(
            entries = emptyList(),
            documents = listOf(lapsedInsurance, document(DocumentType.PUC), document(DocumentType.RC)),
            costTrend = null,
        )

        assertNull(insight)
    }

    /* ------------------------- ordering ------------------------- */

    @Test
    fun `the rarest condition wins over the commoner ones`() {
        val insight = InsightPicker.pick(
            // Resale-ready, a big cost move, and a missing paper all hold at once.
            entries = entries(count = 5, verified = true),
            documents = emptyList(),
            costTrend = CostTrend(percentChange = 30),
        )

        assertIs<CarInsight.ResaleReady>(insight)
    }
}
