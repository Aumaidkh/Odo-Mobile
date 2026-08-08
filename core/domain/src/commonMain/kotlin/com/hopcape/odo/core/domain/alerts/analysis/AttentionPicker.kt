package com.hopcape.odo.core.domain.alerts.analysis

import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.model.latestOfType
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import com.hopcape.odo.core.domain.servicelog.policy.ServiceIntervalPolicy
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate

/**
 * Picks the one thing about a car that most needs the owner's attention.
 *
 * A pure domain service — no repository, no clock. It reads the car's papers and its
 * service record and returns a single [CarAttention], or `null` when nothing is due.
 *
 * It answers the question in one place because two surfaces ask it: Home's attention card
 * now, and the reminder engine (M4) later. A dashboard that says "service due" while a push
 * stays quiet is the kind of disagreement that makes owners stop trusting both.
 *
 * The two rules that are not obvious from the types:
 *
 * - **Only papers Odo actually chases can raise attention.** Chase-worthiness comes from
 *   [DocumentReminderPolicy], which already decided that an RC, a loan letter or an "other"
 *   document has no renewal worth nagging about. Reading it here rather than restating it
 *   means the card and the push can never disagree about which papers matter.
 * - **The renewal window is the paper's own longest lead time**, again from the same policy:
 *   30 days for insurance, 15 for a PUC. Using one window for every type would have Home
 *   prompting about a PUC two weeks before the engine has anything to say.
 */
object AttentionPicker {

    /**
     * What needs attention on [today], or `null` when nothing does.
     *
     * The order is what the owner stands to lose. A lapsed paper comes first because
     * driving on one is an offence in India. An overdue service comes next: the deadline
     * has already passed, unlike a paper that is merely close to expiry. Then the paper
     * inside its renewal window, then the service approaching its interval.
     *
     * @param documents every non-deleted document for the car.
     * @param entries every non-deleted service log for the car.
     * @param currentOdometer the car's odometer right now — the latest manual reading plus
     *   any counted auto-trips since it (`CurrentOdometerProvider`) — which is what the
     *   service interval measures distance against. `null` when the car has no reading at all.
     */
    fun pick(
        documents: List<Document>,
        entries: List<ServiceLogEntry>,
        currentOdometer: Distance?,
        today: LocalDate,
    ): CarAttention? {
        val papers = chasedDocuments(documents, today)
        val last = entries.maxWithOrNull(compareBy({ it.serviceDate }, { it.odometer.km }))
        val service = ServiceIntervalPolicy.statusFor(
            lastServiceDate = last?.serviceDate,
            lastServiceOdometer = last?.odometer,
            currentOdometer = currentOdometer,
            today = today,
        )

        return papers.lapsed()
            ?: service.overdue()
            ?: papers.expiring()
            ?: service.due()
    }

    /**
     * The document that speaks for each chased type today, paired with its standing.
     *
     * Only the latest of each type is considered ([latestOfType]) — a renewal is filed as a
     * new document, so last year's lapsed policy sits in the vault beside this year's live
     * one and must not raise an alarm the owner has already dealt with.
     */
    private fun chasedDocuments(
        documents: List<Document>,
        today: LocalDate,
    ): List<Pair<Document, DocumentValidity>> = DocumentType.entries.mapNotNull { type ->
        val window = DocumentReminderPolicy.leadDaysFor(type).maxOrNull() ?: return@mapNotNull null
        val document = documents.latestOfType(type) ?: return@mapNotNull null
        document to DocumentValidity.of(document.expiresOn, today, renewalWindowDays = window)
    }

    /** The most important lapsed paper, ties going to the one that lapsed longest ago. */
    private fun List<Pair<Document, DocumentValidity>>.lapsed(): CarAttention? =
        mapNotNull { (document, validity) ->
            (validity as? DocumentValidity.Expired)?.let { document to it }
        }
            .minWithOrNull(compareBy({ it.first.type.priority }, { it.second.since }))
            ?.let { (document, expired) ->
                CarAttention.DocumentLapsed(
                    documentId = document.id,
                    type = document.type,
                    since = expired.since,
                    daysAgo = expired.daysAgo,
                )
            }

    /** The paper expiring soonest, ties going to the more important type. */
    private fun List<Pair<Document, DocumentValidity>>.expiring(): CarAttention? =
        mapNotNull { (document, validity) ->
            (validity as? DocumentValidity.ExpiringSoon)?.let { document to it }
        }
            .minWithOrNull(compareBy({ it.second.until }, { it.first.type.priority }))
            ?.let { (document, expiring) ->
                CarAttention.DocumentExpiring(
                    documentId = document.id,
                    type = document.type,
                    until = expiring.until,
                    daysLeft = expiring.daysLeft,
                )
            }

    private fun ServiceDueStatus.overdue(): CarAttention? = (this as? ServiceDueStatus.Overdue)
        ?.let { CarAttention.ServiceOverdue(daysOverdue = it.daysOverdue, kmOverdue = it.kmOverdue) }

    private fun ServiceDueStatus.due(): CarAttention? = (this as? ServiceDueStatus.DueSoon)
        ?.let { CarAttention.ServiceDue(daysLeft = it.daysLeft, kmLeft = it.kmLeft) }

    /**
     * Which paper to raise first when more than one is in trouble. Insurance leads because
     * it is both the illegal one to drive without and the expensive one to be caught
     * without; a PUC is a same-day test; a licence renewal means an RTO appointment but
     * says nothing about the car.
     *
     * Lower sorts first. The types Odo does not chase never reach here.
     */
    private val DocumentType.priority: Int
        get() = when (this) {
            DocumentType.INSURANCE -> 0
            DocumentType.PUC -> 1
            DocumentType.LICENCE -> 2
            DocumentType.RC, DocumentType.LOAN, DocumentType.OTHER -> 3
        }
}
