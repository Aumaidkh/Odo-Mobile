package com.hopcape.odo.core.domain.record.model

import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate

/**
 * A car's whole record, as the shareable document prints it.
 *
 * The shape a buyer reads, not the shape the app stores: one flat object holding every block
 * of the printed page — who the car is, the four headline figures, the ownership lines, the
 * papers on file, the entries, and what they add up to.
 *
 * Data only. Nothing here is a formatted string, a label or a colour: dates stay [LocalDate],
 * money stays [Amount], distance stays [Distance], and the kind of a thing stays an enum. The
 * renderer turns those into "12 Jul 2026", "Rs. 3,200" and "Verified", because that copy is
 * user-facing text and belongs to a feature's string resources.
 *
 * Shared kernel rather than a feature's own type, for the same reason as
 * [ActivityEvent][com.hopcape.odo.core.domain.activity.model.ActivityEvent]: the record spans
 * the car, its services, its documents and its score, and no single feature owns all four.
 */
data class ServiceRecord(
    /** What the car is called, nickname included — the document's headline. */
    val carName: String,
    /** Make, model and trim with no nickname, for the running header. */
    val modelName: String,
    val modelYear: Int?,
    val fuelType: FuelType?,
    /** Normalised, unspaced. The renderer groups it for display. */
    val registrationNumber: String?,
    /** The car's reading now, which is not necessarily the newest entry's. */
    val odometer: Distance?,
    /** Out of 100. Null until the car has ever been scored. */
    val healthScore: Int?,
    /** The earliest day the record covers — the oldest entry, not the day Odo was installed. */
    val recordSince: LocalDate?,
    val ownership: Ownership,
    /** The car's papers, newest of each type. Empty when nothing is filed. */
    val documents: List<DocumentOnFile>,
    /** Every entry, newest first. */
    val rows: List<RecordRow>,
    /** What the entries with an amount add up to. */
    val total: Amount,
    /** The day the document was produced. */
    val issuedOn: LocalDate,
) {
    /** How many entries the record holds — the "ENTRIES" figure. */
    val entryCount: Int get() = rows.size

    /** How many of them are bill-backed — the "VERIFIED n of m" figure. */
    val verifiedCount: Int get() = rows.count { it.status == RecordStatus.VERIFIED }

    /** True when there is nothing to print but the car itself. */
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * The ownership block.
 *
 * Deliberately three lines and not four: the printed template also asked for an accident
 * history, and Odo has never asked the owner for one. A resale document that says "None
 * declared" where nothing was declared makes a claim on the owner's behalf, so the line is
 * absent rather than assumed. It comes back if and when the car aggregate carries a
 * declaration.
 */
data class Ownership(
    /** The owner's name as they gave it, or null if they never did. */
    val ownerName: String?,
    /**
     * The year the owner bought the car. A year and not a month: [PurchaseYear] is all Odo
     * collects, and printing "August 2020" from a value that only says 2020 would invent the
     * month.
     */
    val ownedSince: Int?,
    /**
     * Whether every logged reading moves forward in time. False means at least one entry
     * records fewer kilometres than an entry dated before it, which is the single thing a
     * buyer is looking for.
     */
    val odometerConsistent: Boolean,
)

/** One paper on file, and how long it is good for. */
data class DocumentOnFile(
    val type: DocumentType,
    val validity: DocumentValidity,
)

/**
 * One printed line of the record.
 *
 * Wraps the [event] the activity feed already built rather than restating it, so the PDF and
 * the Timeline tab cannot end up describing the same day differently. [status] is the one
 * thing the feed does not carry, because only a printed record has to say out loud whether a
 * line is backed by a bill.
 */
data class RecordRow(
    val event: ActivityEvent,
    val status: RecordStatus,
) {
    val date: LocalDate get() = event.date

    /** The reading at the time, or null for a line that is not a visit to a workshop. */
    val odometer: Distance? get() = (event as? ActivityEvent.Service)?.odometer

    /** What it cost, or null where Odo holds no figure — every non-service line today. */
    val amount: Amount? get() = (event as? ActivityEvent.Service)?.amount
}

/**
 * Whether a line can be checked against a document, or rests on the owner's word.
 *
 * The same distinction as
 * [VerificationStatus][com.hopcape.odo.core.domain.servicelog.model.VerificationStatus], but
 * it covers every kind of line the record prints, not only services. Kept separate so the
 * rule for a document row or the opening milestone is written once, here, instead of at each
 * place that draws a badge.
 */
enum class RecordStatus { VERIFIED, SELF_REPORTED }
