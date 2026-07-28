package com.hopcape.odo.feature.timeline.domain.model

import com.hopcape.odo.core.domain.servicelog.model.RecordScore
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.WorkshopName
import kotlinx.datetime.LocalDate

/**
 * One event on a car's timeline — this feature's own domain model of the unified
 * activity feed it aggregates (services · documents · health-score changes ·
 * milestones).
 *
 * Timeline owns the *aggregate* type (no other feature needs it), but every part it
 * is built from is a shared-kernel value object from `:core:domain` — [ServiceLogId],
 * [Amount], [Distance], [WorkshopName], [RecordScore], [VerificationStatus],
 * [LocalDate]. So rupees can never be passed where kilometres are expected, an id
 * can't be confused with a title, and rendering (₹ / km / dates) stays the UI's job.
 *
 * The feed is ordered — and month-grouped — by [date], newest first.
 */
internal sealed interface TimelineEvent {

    /** The day the event happened. */
    val date: LocalDate

    /**
     * A logged service — the only event that opens a detail screen (the shared
     * `OdoDestination.ServiceLog.Detail`, keyed by [id]).
     */
    data class Service(
        val id: ServiceLogId,
        /** One-line "what was done", e.g. "Oil change + filter". */
        val workDone: String,
        /** Absent when the owner didn't record where the work was done. */
        val workshop: WorkshopName?,
        val odometer: Distance,
        val amount: Amount,
        val verification: VerificationStatus,
        /**
         * How far over the city average the fairness check judged this entry — null
         * when it was fair, or when no check was possible. Read through [trust]
         * rather than directly.
         */
        val overchargedBy: Amount?,
        override val date: LocalDate,
    ) : TimelineEvent

    /** A renewed document — [validTill] is absent when the paper carries no expiry. */
    data class DocumentRenewed(
        val document: DocumentKind,
        val validTill: LocalDate?,
        override val date: LocalDate,
    ) : TimelineEvent

    /** A health-score move; the direction is simply [from] vs [to]. */
    data class HealthScoreChanged(
        val from: RecordScore,
        val to: RecordScore,
        override val date: LocalDate,
    ) : TimelineEvent

    /** The milestone every car's story starts with. */
    data class CarAdded(
        val carName: String,
        override val date: LocalDate,
    ) : TimelineEvent
}

/** The kind of paper a [TimelineEvent.DocumentRenewed] refers to. */
internal enum class DocumentKind { INSURANCE, PUC, RC, LICENCE }

/**
 * The trust state a service node renders — the Odo trust model as three mutually
 * exclusive cases, so the UI's `when` is exhaustive and [Flagged] carries its amount
 * instead of the screen re-reading a nullable field.
 */
internal sealed interface ServiceTrust {
    /** Bill-backed and priced fairly. */
    data object Verified : ServiceTrust

    /** Bill-backed, but the fairness check judged it [overchargedBy] over the average. */
    data class Flagged(val overchargedBy: Amount) : ServiceTrust

    /** No bill attached — nothing to verify or benchmark against (PRD guardrail). */
    data object SelfReported : ServiceTrust
}

/**
 * Derived, never stored — the same rule as `ServiceLogEntry.verification` in the
 * kernel, kept here so every timeline surface reads trust the same way. Only
 * bill-backed entries can be fairness-checked, so the cases can't overlap.
 */
internal val TimelineEvent.Service.trust: ServiceTrust
    get() = when {
        overchargedBy != null -> ServiceTrust.Flagged(overchargedBy)
        verification == VerificationStatus.VERIFIED -> ServiceTrust.Verified
        else -> ServiceTrust.SelfReported
    }
