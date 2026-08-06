package com.hopcape.odo.core.domain.reminder.analysis

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.reminder.model.ReminderUrgency
import com.hopcape.odo.core.domain.reminder.model.UpcomingReminder
import com.hopcape.odo.core.domain.reminder.policy.ReminderOccurrence
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReminderFeedPolicyTest {

    private val today = LocalDate(2026, 8, 6)

    private fun feed(
        documents: List<Document> = emptyList(),
        serviceStatus: ServiceDueStatus = ServiceDueStatus.NeverServiced,
        customs: List<CustomReminder> = emptyList(),
        dismissals: List<ReminderDismissal> = emptyList(),
        currentKm: Int? = null,
    ) = ReminderFeedPolicy.feedFor(documents, serviceStatus, customs, dismissals, today, currentKm)

    private fun document(
        type: DocumentType = DocumentType.INSURANCE,
        expiresOn: LocalDate?,
        id: String = "doc-1",
    ): Document = Document.reconstitute(
        id = DocumentId(id),
        ownerId = OwnerId("owner-1"),
        carId = CarId("car-1"),
        type = type,
        storagePath = "documents/car-1/$id.pdf",
        source = DocumentSource.UPLOADED,
        addedOn = LocalDate(2026, 1, 1),
        expiresOn = expiresOn,
    )

    private fun custom(
        id: String = "rem-1",
        cadence: ReminderCadence = ReminderCadence.EveryDays(15),
        startsOn: LocalDate = today,
        preset: ReminderPreset? = null,
        anchorKm: Int? = null,
    ): CustomReminder = CustomReminder.create(
        id = com.hopcape.odo.core.domain.reminder.model.ReminderId(id),
        ownerId = OwnerId("owner-1"),
        carId = CarId("car-1"),
        title = "Air pressure check",
        cadence = cadence,
        startsOn = startsOn,
        at = LocalTime(9, 0),
        today = today,
        preset = preset,
        anchorKm = anchorKm,
    ).getOrNull()!!

    /* ---- Documents ---- */

    @Test
    fun insuranceExpiringWithinAWeekLandsInThisWeek() {
        val feed = feed(documents = listOf(document(expiresOn = today.plus(5, DateTimeUnit.DAY))))

        val row = feed.thisWeek.single()
        assertIs<UpcomingReminder.DocumentRenewal>(row)
        assertEquals(ReminderKind.INSURANCE_EXPIRY, row.kind)
        assertEquals(5, row.daysLeft)
    }

    @Test
    fun insuranceInsideTheWindowButPastAWeekIsDueSoon() {
        val feed = feed(documents = listOf(document(expiresOn = today.plus(20, DateTimeUnit.DAY))))

        val row = feed.upcoming.single()
        assertEquals(ReminderUrgency.DUE_SOON, row.urgency)
    }

    @Test
    fun validInsuranceFarOutIsOnTrack() {
        val feed = feed(documents = listOf(document(expiresOn = today.plus(200, DateTimeUnit.DAY))))

        val row = feed.upcoming.single()
        assertEquals(ReminderUrgency.ON_TRACK, row.urgency)
    }

    @Test
    fun lapsedPapersYieldNoRow() {
        val feed = feed(documents = listOf(document(expiresOn = today.plus(-1, DateTimeUnit.DAY))))
        assertTrue(feed.thisWeek.isEmpty() && feed.upcoming.isEmpty())
    }

    @Test
    fun lifetimePapersAreNeverChased() {
        val feed = feed(
            documents = listOf(
                document(type = DocumentType.RC, expiresOn = today.plus(3, DateTimeUnit.DAY)),
            ),
        )
        assertTrue(feed.thisWeek.isEmpty() && feed.upcoming.isEmpty())
    }

    @Test
    fun theLatestExpiryOfATypeWins() {
        val feed = feed(
            documents = listOf(
                document(expiresOn = today.plus(3, DateTimeUnit.DAY), id = "doc-old"),
                document(expiresOn = today.plus(300, DateTimeUnit.DAY), id = "doc-new"),
            ),
        )

        val row = feed.upcoming.single()
        assertIs<UpcomingReminder.DocumentRenewal>(row)
        assertEquals(DocumentId("doc-new"), row.documentId)
        assertTrue(feed.thisWeek.isEmpty())
    }

    @Test
    fun dismissingTheCurrentNudgeHidesTheRow() {
        val expiresOn = today.plus(5, DateTimeUnit.DAY)
        val document = document(expiresOn = expiresOn)
        val visible = feed(documents = listOf(document))
        val nudge = (visible.thisWeek.single() as UpcomingReminder.DocumentRenewal).currentNudgeOn!!

        val afterDismiss = feed(
            documents = listOf(document),
            dismissals = listOf(ReminderDismissal(ReminderKind.INSURANCE_EXPIRY, nudge)),
        )

        assertTrue(afterDismiss.thisWeek.isEmpty())
    }

    /* ---- Service ---- */

    @Test
    fun neverServicedYieldsNoServiceRow() {
        assertTrue(feed().thisWeek.isEmpty() && feed().upcoming.isEmpty())
    }

    @Test
    fun overdueServiceLandsInThisWeek() {
        val feed = feed(serviceStatus = ServiceDueStatus.Overdue(daysOverdue = 12, kmOverdue = null))

        val row = feed.thisWeek.single()
        assertIs<UpcomingReminder.ServiceDue>(row)
        assertEquals(ReminderKind.SERVICE_DUE_TIME, row.kind)
    }

    @Test
    fun kmOnlyOverdueIsTheKmKind() {
        val feed = feed(serviceStatus = ServiceDueStatus.Overdue(daysOverdue = 0, kmOverdue = 400))

        val row = feed.thisWeek.single()
        assertIs<UpcomingReminder.ServiceDue>(row)
        assertEquals(ReminderKind.SERVICE_DUE_KM, row.kind)
    }

    @Test
    fun serviceDueSoonOnDistanceAloneIsTheKmKind() {
        // 90 days of interval left, but only 800 km — the distance half triggered it.
        val feed = feed(serviceStatus = ServiceDueStatus.DueSoon(daysLeft = 90, kmLeft = 800))

        val row = feed.upcoming.single()
        assertIs<UpcomingReminder.ServiceDue>(row)
        assertEquals(ReminderKind.SERVICE_DUE_KM, row.kind)
        assertEquals(ReminderUrgency.DUE_SOON, row.urgency)
    }

    @Test
    fun serviceComfortablyInsideTheIntervalIsOnTrack() {
        val feed = feed(serviceStatus = ServiceDueStatus.NotDue(daysLeft = 150, kmLeft = 8_000))
        assertEquals(ReminderUrgency.ON_TRACK, feed.upcoming.single().urgency)
    }

    /* ---- Customs ---- */

    @Test
    fun customDueWithinAWeekLandsInThisWeek() {
        val feed = feed(customs = listOf(custom(startsOn = today.plus(3, DateTimeUnit.DAY))))

        val row = feed.thisWeek.single()
        assertIs<UpcomingReminder.Custom>(row)
        assertEquals(ReminderOccurrence.OnDate(today.plus(3, DateTimeUnit.DAY)), row.occurrence)
    }

    @Test
    fun dismissedOccurrenceShowsTheOneBehindIt() {
        val reminder = custom(startsOn = today.plus(3, DateTimeUnit.DAY))

        val feed = feed(
            customs = listOf(reminder),
            dismissals = listOf(
                ReminderDismissal(
                    ReminderKind.CUSTOM,
                    today.plus(3, DateTimeUnit.DAY),
                    reminder.id,
                ),
            ),
        )

        val row = feed.upcoming.single()
        assertIs<UpcomingReminder.Custom>(row)
        assertEquals(ReminderOccurrence.OnDate(today.plus(18, DateTimeUnit.DAY)), row.occurrence)
    }

    @Test
    fun dismissedOnceReminderIsGone() {
        val reminder = custom(cadence = ReminderCadence.Once, startsOn = today.plus(3, DateTimeUnit.DAY))

        val feed = feed(
            customs = listOf(reminder),
            dismissals = listOf(
                ReminderDismissal(
                    ReminderKind.CUSTOM,
                    today.plus(3, DateTimeUnit.DAY),
                    reminder.id,
                ),
            ),
        )

        assertTrue(feed.thisWeek.isEmpty() && feed.upcoming.isEmpty())
    }

    @Test
    fun pausedCustomYieldsNoRowButStillClaimsItsPreset() {
        val paused = custom(preset = ReminderPreset.AIR_PRESSURE).withPaused(true)
        val feed = feed(customs = listOf(paused))

        assertTrue(feed.thisWeek.isEmpty() && feed.upcoming.isEmpty())
        assertTrue(ReminderPreset.AIR_PRESSURE !in feed.suggestions)
    }

    @Test
    fun distanceTargetAlreadyPassedIsThisWeek() {
        val feed = feed(
            customs = listOf(
                custom(cadence = ReminderCadence.EveryDistance(10_000), anchorKm = 42_000),
            ),
            currentKm = 52_500,
        )

        val row = feed.thisWeek.single()
        assertIs<UpcomingReminder.Custom>(row)
        assertEquals(ReminderOccurrence.AtOdometer(52_000), row.occurrence)
    }

    @Test
    fun distanceTargetWithoutAnOdometerIsOnTrack() {
        val feed = feed(
            customs = listOf(
                custom(cadence = ReminderCadence.EveryDistance(10_000), anchorKm = 42_000),
            ),
        )
        assertEquals(ReminderUrgency.ON_TRACK, feed.upcoming.single().urgency)
    }

    /* ---- Suggestions & ordering ---- */

    @Test
    fun untakenPresetsAreSuggested() {
        val feed = feed(customs = listOf(custom(preset = ReminderPreset.BATTERY)))

        assertTrue(ReminderPreset.BATTERY !in feed.suggestions)
        assertContains(feed.suggestions, ReminderPreset.COOLANT)
    }

    @Test
    fun upcomingIsOrderedMostUrgentFirst() {
        val feed = feed(
            documents = listOf(document(expiresOn = today.plus(200, DateTimeUnit.DAY))),
            serviceStatus = ServiceDueStatus.DueSoon(daysLeft = 20, kmLeft = null),
        )

        assertEquals(ReminderUrgency.DUE_SOON, feed.upcoming.first().urgency)
        assertEquals(ReminderUrgency.ON_TRACK, feed.upcoming.last().urgency)
    }
}
