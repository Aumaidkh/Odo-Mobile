package com.hopcape.odo.core.domain.document.policy

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentReminderPolicyTest {

    private val today = LocalDate(2026, 7, 28)

    private fun document(
        type: DocumentType = DocumentType.INSURANCE,
        expiresOn: LocalDate? = LocalDate(2027, 7, 3),
    ) = Document.reconstitute(
        id = DocumentId("doc-1"),
        ownerId = OwnerId("owner-1"),
        carId = CarId("car-1"),
        type = type,
        storagePath = "documents/car-1/doc-1.pdf",
        source = DocumentSource.UPLOADED,
        addedOn = null,
        expiresOn = expiresOn,
    )

    @Test
    fun insurance_followsThePrdTriggerTable() {
        assertContentEquals(listOf(30, 7, 1), DocumentReminderPolicy.leadDaysFor(DocumentType.INSURANCE))
    }

    @Test
    fun puc_followsThePrdTriggerTable() {
        assertContentEquals(listOf(15, 3), DocumentReminderPolicy.leadDaysFor(DocumentType.PUC))
    }

    @Test
    fun papersWithNoRenewal_areNeverChased() {
        listOf(DocumentType.RC, DocumentType.LOAN, DocumentType.OTHER).forEach { type ->
            assertTrue(DocumentReminderPolicy.leadDaysFor(type).isEmpty(), "$type should not be chased")
        }
    }

    @Test
    fun schedule_countsBackFromExpiry_earliestFirst() {
        val schedule = DocumentReminderPolicy.scheduleFor(
            type = DocumentType.PUC,
            expiresOn = LocalDate(2026, 11, 12),
            today = today,
        )

        assertContentEquals(
            listOf(
                DocumentReminder(daysBefore = 15, on = LocalDate(2026, 10, 28)),
                DocumentReminder(daysBefore = 3, on = LocalDate(2026, 11, 9)),
            ),
            schedule,
        )
    }

    @Test
    fun schedule_dropsNudgesWhoseDayHasPassed() {
        // Insurance expiring in 8 days: the 30-day nudge is long gone, 7 and 1 remain.
        val schedule = DocumentReminderPolicy.scheduleFor(
            type = DocumentType.INSURANCE,
            expiresOn = LocalDate(2026, 8, 5),
            today = today,
        )

        assertContentEquals(listOf(7, 1), schedule.map { it.daysBefore })
        assertEquals(LocalDate(2026, 7, 29), schedule.first().on)
    }

    @Test
    fun schedule_keepsANudgeFallingExactlyToday() {
        // 30 days before 27 Aug is 28 Jul — today. It has not passed, so it still counts.
        val schedule = DocumentReminderPolicy.scheduleFor(
            type = DocumentType.INSURANCE,
            expiresOn = LocalDate(2026, 8, 27),
            today = today,
        )

        assertEquals(30, schedule.first().daysBefore)
        assertEquals(today, schedule.first().on)
    }

    @Test
    fun lifetimePaper_hasNoSchedule() {
        assertTrue(DocumentReminderPolicy.scheduleFor(document(expiresOn = null), today).isEmpty())
    }

    @Test
    fun lapsedPaper_hasNoSchedule_becauseACountdownIsNoLongerTheRightNudge() {
        val lapsed = document(expiresOn = LocalDate(2026, 7, 3))

        assertTrue(DocumentReminderPolicy.scheduleFor(lapsed, today).isEmpty())
        assertNull(DocumentReminderPolicy.nextReminderFor(lapsed, today))
    }

    @Test
    fun nextReminder_isTheSoonestOne() {
        val next = DocumentReminderPolicy.nextReminderFor(document(), today)!!

        assertEquals(30, next.daysBefore)
        assertEquals(LocalDate(2027, 6, 3), next.on)
    }

    @Test
    fun documentOverload_agreesWithTheTypeOverload() {
        val doc = document(type = DocumentType.LICENCE, expiresOn = LocalDate(2031, 8, 14))

        assertContentEquals(
            DocumentReminderPolicy.scheduleFor(doc.type, doc.expiresOn, today),
            DocumentReminderPolicy.scheduleFor(doc, today),
        )
    }
}
