package com.hopcape.odo.core.domain.activity.model

import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.servicelog.model.RecordScore
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.WorkshopName
import kotlinx.datetime.LocalDate

/**
 * One event in a car's activity feed — a service, a document filed, a health-score move, or
 * the milestone the car's record starts with.
 *
 * Shared kernel rather than a feature's own type, because two surfaces render the same feed:
 * the Timeline tab shows all of it, and Home shows the most recent row or two. A feature may
 * not import a feature, so the merged view of "what has happened to this car" belongs here,
 * next to [ActivityFeedBuilder][com.hopcape.odo.core.domain.activity.analysis.ActivityFeedBuilder]
 * which assembles it.
 *
 * Every part is a shared value object — [Amount], [Distance], [WorkshopName], [WorkDone] —
 * so rupees can never be passed where kilometres are expected and rendering (₹ / km / dates,
 * and the copy for a category or a document type) stays each feature's own job.
 */
sealed interface ActivityEvent {

    /** The day the event happened, in the owner's zone. */
    val date: LocalDate

    /**
     * A logged service — the only event that opens a detail screen, keyed by [id].
     */
    data class Service(
        val id: ServiceLogId,
        /** What was done, as the entry itself describes it; the UI resolves the copy. */
        val workDone: WorkDone,
        /** Absent when the owner didn't record where the work was done. */
        val workshop: WorkshopName?,
        val odometer: Distance,
        val amount: Amount,
        val verification: VerificationStatus,
        /**
         * How far over the city average the stored fairness verdict judged this entry —
         * null when it was fair, or when no check was possible.
         */
        val overchargedBy: Amount?,
        override val date: LocalDate,
    ) : ActivityEvent

    /**
     * A document filed in the vault. [isRenewal] is false for the first document of its
     * type and true for every later one, so a first insurance upload doesn't claim to be a
     * renewal it wasn't.
     *
     * [id] is the document itself: two papers of one type can be filed on the same day, so
     * the type and the date do not identify a row.
     */
    data class DocumentFiled(
        val id: DocumentId,
        val document: DocumentType,
        val isRenewal: Boolean,
        /** Absent when the paper carries no expiry — see `DocumentValidity.NoExpiry`. */
        val validTill: LocalDate?,
        override val date: LocalDate,
    ) : ActivityEvent

    /**
     * A day on which the health score moved; the direction is [from] vs [to].
     *
     * One event per day rather than one per stored snapshot: a snapshot is written whenever
     * the score changes, so an afternoon of uploading documents would otherwise post a row
     * for every point.
     */
    data class ScoreChanged(
        val from: RecordScore,
        val to: RecordScore,
        override val date: LocalDate,
    ) : ActivityEvent

    /**
     * A tank of fuel, however it was captured.
     *
     * On the feed because it is the one thing an owner does to their car more often than
     * anything else, and a record that shows every service but no refuelling is not a
     * history of the car — it is a history of its repairs.
     *
     * [station] is absent for a fill typed in without one. [quantityMilli] is thousandths of
     * a [unit], the resolution fills are stored at, and it stays a number rather than a
     * formatted string for the same reason [Amount] and [Distance] do: the feature that
     * renders it decides whether that reads as litres, kilograms or kWh.
     */
    data class FuelFilled(
        val id: FuelFillId,
        val station: String?,
        val quantityMilli: Long,
        val unit: FuelUnit,
        val amount: Amount,
        /** Null for a fill confirmed without one — the reading is optional on a fill. */
        val odometer: Distance?,
        override val date: LocalDate,
    ) : ActivityEvent

    /** The milestone every car's record starts with. */
    data class CarAdded(
        val carName: String,
        override val date: LocalDate,
    ) : ActivityEvent
}
