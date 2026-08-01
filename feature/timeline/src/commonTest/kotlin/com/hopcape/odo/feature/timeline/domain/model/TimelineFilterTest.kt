package com.hopcape.odo.feature.timeline.domain.model

import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.servicelog.model.RecordScore
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import arrow.core.getOrElse
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineFilterTest {

    private fun service(id: String, overchargedByPaise: Long? = null) = ActivityEvent.Service(
        id = ServiceLogId(id),
        workDone = WorkDone.Unspecified,
        workshop = null,
        odometer = Distance.of(50_000).getOrElse { error("fixture") },
        amount = Amount.of(300_000).getOrElse { error("fixture") },
        verification = VerificationStatus.SELF_REPORTED,
        overchargedBy = overchargedByPaise?.let { Amount.of(it).getOrElse { error("fixture") } },
        date = LocalDate(2026, 7, 12),
    )

    private val document = ActivityEvent.DocumentFiled(
        id = DocumentId("doc-puc"),
        document = DocumentType.PUC,
        isRenewal = false,
        validTill = null,
        date = LocalDate(2026, 6, 2),
    )
    private val score = ActivityEvent.ScoreChanged(
        from = RecordScore.of(70),
        to = RecordScore.of(74),
        date = LocalDate(2026, 7, 8),
    )
    private val milestone = ActivityEvent.CarAdded(carName = "Swift VXI", date = LocalDate(2026, 1, 5))

    private val events = listOf(service("plain"), service("flagged", 70_000), document, score, milestone)

    @Test
    fun theDefaultHidesNothing() {
        val filter = TimelineFilter()

        assertTrue(filter.hidesNothing)
        assertEquals(events, filter.apply(events))
    }

    @Test
    fun untickingACategoryDropsIt() {
        val filter = TimelineFilter().withCategory(ActivityCategory.DOCUMENTS, selected = false)

        assertEquals(listOf(service("plain"), service("flagged", 70_000), score, milestone), filter.apply(events))
        assertEquals(false, filter.hidesNothing)
    }

    @Test
    fun onlyFlaggedKeepsTheOverchargedServices() {
        val filter = TimelineFilter(onlyFlagged = true)

        assertEquals(listOf(service("flagged", 70_000)), filter.apply(events))
    }

    @Test
    fun onlyFlaggedNarrowsWithinTheCategoriesRatherThanOverridingThem() {
        // Services off and "only flagged" on is a contradiction, and it shows nothing rather
        // than quietly bringing the services back.
        val filter = TimelineFilter(onlyFlagged = true)
            .withCategory(ActivityCategory.SERVICES, selected = false)

        assertEquals(emptyList(), filter.apply(events))
    }

    @Test
    fun everyEventKindBelongsToExactlyOneCategory() {
        assertEquals(ActivityCategory.SERVICES, service("plain").category)
        assertEquals(ActivityCategory.DOCUMENTS, document.category)
        assertEquals(ActivityCategory.SCORE, score.category)
        assertEquals(ActivityCategory.MILESTONES, milestone.category)
    }
}
