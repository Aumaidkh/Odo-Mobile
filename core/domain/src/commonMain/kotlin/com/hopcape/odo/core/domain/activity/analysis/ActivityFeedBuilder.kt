package com.hopcape.odo.core.domain.activity.analysis

import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.servicelog.model.RecordScore
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.servicelog.model.workDone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Builds a car's activity feed from everything that has happened to it.
 *
 * Pure and deterministic: the same inputs always produce the same feed, in the same order.
 * It reads no clock and no storage, so the Timeline tab and Home's recent row can call it
 * with the same data and cannot disagree about what happened.
 *
 * The rules that are not obvious from the types:
 *
 * - **A document's date is the day it was filed**, not the day it was issued. A policy
 *   uploaded today can carry a start date from months ago, and filing it is the thing that
 *   happened today.
 * - **The first document of a type is not a renewal**; every later one of the same type is.
 * - **Score moves are one event per day, not one per snapshot.** Snapshots are written
 *   whenever the score changes, so a day of uploading documents would otherwise post a row
 *   per point. A day whose net move is zero produces nothing.
 * - **A move across two rule versions is dropped.** A release that changes the point rules
 *   moves every car's score, and a feed claiming the car improved that day would be
 *   describing the release.
 */
object ActivityFeedBuilder {

    /**
     * The car's feed, newest first.
     *
     * @param car the car itself, for the opening milestone; `null` while it is still loading.
     * @param zone the owner's zone, used to place a stored instant on a calendar day.
     */
    fun build(
        car: Car?,
        entries: List<ServiceLogEntry>,
        documents: List<Document>,
        scores: List<HealthSnapshot>,
        zone: TimeZone,
    ): List<ActivityEvent> {
        val events = serviceEvents(entries) +
            documentEvents(documents) +
            scoreEvents(scores, zone) +
            milestoneEvents(car)

        // Newest first, then by a fixed rank so two events on one day always come back in
        // the same order. Without the rank the order would depend on which list happened to
        // be built first, and the feed would reshuffle on every re-emission.
        return events.sortedWith(
            compareByDescending<ActivityEvent> { it.date }.thenBy { it.rank },
        )
    }

    private fun serviceEvents(entries: List<ServiceLogEntry>): List<ActivityEvent> =
        entries.map { entry ->
            ActivityEvent.Service(
                id = entry.id,
                workDone = entry.workDone(),
                workshop = entry.workshopName,
                odometer = entry.odometer,
                amount = entry.totalAmount,
                verification = entry.verification,
                // The verdict stored when the entry became checkable, never a fresh one:
                // the figure the owner was shown is the figure they keep seeing.
                overchargedBy = entry.fairness?.overchargedBy,
                date = entry.serviceDate,
            )
        }

    /**
     * One event per document, with renewals worked out per type in date order. A document
     * with no date at all is skipped rather than guessed at — it cannot be placed on a feed
     * that is ordered by date.
     */
    private fun documentEvents(documents: List<Document>): List<ActivityEvent> =
        documents.groupBy { it.type }
            .flatMap { (type, ofType) ->
                ofType.mapNotNull { document -> document.filedOn()?.let { it to document } }
                    // Ties keep a stable order, so which of two documents filed on the same
                    // day counts as the renewal does not change between reads.
                    .sortedBy { (filedOn, _) -> filedOn }
                    .mapIndexed { index, (filedOn, document) ->
                        ActivityEvent.DocumentFiled(
                            id = document.id,
                            document = type,
                            isRenewal = index > 0,
                            validTill = document.expiresOn,
                            date = filedOn,
                        )
                    }
            }

    /** The day the document was filed, falling back to its issue date for an unstored one. */
    private fun Document.filedOn(): LocalDate? = addedOn ?: issuedOn

    private fun scoreEvents(scores: List<HealthSnapshot>, zone: TimeZone): List<ActivityEvent> {
        if (scores.size < 2) return emptyList()
        val ordered = scores.sortedBy { it.computedAt }

        // Each day's closing score, and the score it opened from — the last one taken before
        // that day. The first day has nothing before it and so produces no event.
        return ordered.groupBy { it.computedAt.toLocalDateTime(zone).date }
            .entries
            .sortedBy { (day, _) -> day }
            .mapNotNull { (day, ofDay) ->
                val closing = ofDay.last()
                val opening = ordered.lastOrNull { it.computedAt < ofDay.first().computedAt }
                    ?: return@mapNotNull null
                // Only scores taken under the same rules can be subtracted from each other.
                if (opening.algoVersion != closing.algoVersion) return@mapNotNull null
                if (opening.score.total == closing.score.total) return@mapNotNull null

                ActivityEvent.ScoreChanged(
                    from = RecordScore.of(opening.score.total),
                    to = RecordScore.of(closing.score.total),
                    date = day,
                )
            }
    }

    private fun milestoneEvents(car: Car?): List<ActivityEvent> {
        val addedOn = car?.addedOn ?: return emptyList()
        return listOf(ActivityEvent.CarAdded(carName = car.displayName, date = addedOn))
    }

    /**
     * Where an event sits among others on the same day. Services first because they are what
     * the owner did; the score move they caused reads underneath it; the milestone is always
     * the bottom of the day it opened.
     */
    private val ActivityEvent.rank: Int
        get() = when (this) {
            is ActivityEvent.Service -> 0
            is ActivityEvent.ScoreChanged -> 1
            is ActivityEvent.DocumentFiled -> 2
            is ActivityEvent.CarAdded -> 3
        }
}
