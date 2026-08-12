package com.hopcape.odo.core.domain.document.policy

import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * When Odo nudges an owner about a paper that is running out.
 *
 * A pure domain service — no repository, no clock, no scheduler. It answers "which days
 * matter for this document?" and nothing else; *delivering* on those days is the reminder
 * engine's job (M4, server-scheduled per the PRD). Keeping the rule here means the
 * add-document success screen can honestly say "we'll remind you on 26 Jun" months before
 * any engine exists, and that the engine, when it lands, reads the same table the screen
 * showed rather than a second copy that can drift from it.
 *
 * Lead times come from the PRD's reminder trigger table (§5.3): insurance at 30/7/1 days,
 * PUC at 15/3. The others the PRD does not list, decided here:
 * - **Licence** — 30 and 7 days. A licence renewal means an RTO appointment, so the
 *   day-before nudge that suits an online insurance purchase would arrive too late to act on.
 * - **RC, loan papers, other** — nothing. An RC is a lifetime document, a loan letter has
 *   no renewal, and a reminder with no action behind it is the noise that gets an app muted.
 */
object DocumentReminderPolicy {

    /**
     * How many days before expiry this kind of paper is worth a nudge, longest lead first.
     * Empty for the types that never need renewing.
     */
    fun leadDaysFor(type: DocumentType): List<Int> = when (type) {
        DocumentType.INSURANCE -> INSURANCE_LEAD_DAYS
        DocumentType.PUC -> PUC_LEAD_DAYS
        DocumentType.LICENCE -> LICENCE_LEAD_DAYS
        DocumentType.RC, DocumentType.LOAN, DocumentType.OTHER -> emptyList()
    }

    /**
     * The nudges still ahead of [today] for a document expiring on [expiresOn], earliest
     * first — what the success screen shows and what the engine will schedule.
     *
     * Reminders whose day has already passed are dropped rather than fired late: a
     * "30 days left" push sent on the day of expiry is worse than silence. A document with
     * no expiry, or one already lapsed, therefore yields an empty list — a lapsed paper
     * needs a *renew now* prompt, which is the vault's business, not a countdown's.
     */
    fun scheduleFor(
        type: DocumentType,
        expiresOn: LocalDate?,
        today: LocalDate,
    ): List<DocumentReminder> {
        if (expiresOn == null) return emptyList()
        return leadDaysFor(type)
            .map { DocumentReminder(daysBefore = it, on = expiresOn.minus(it, DateTimeUnit.DAY)) }
            .filter { it.on >= today }
            .sortedBy { it.on }
    }

    /** [scheduleFor] for a document that already exists. */
    fun scheduleFor(document: Document, today: LocalDate): List<DocumentReminder> =
        scheduleFor(document.type, document.expiresOn, today)

    /**
     * The next nudge only — the single line the add-document success screen renders
     * ("we'll remind you on 26 Jun, 7 days before it expires"). `null` when nothing is due:
     * a lifetime paper, a lapsed one, or a type Odo does not chase.
     */
    fun nextReminderFor(document: Document, today: LocalDate): DocumentReminder? =
        scheduleFor(document, today).firstOrNull()

    /** PRD §5.3 — push + WhatsApp, the renewal that costs the most to miss. */
    private val INSURANCE_LEAD_DAYS = listOf(30, 7, 1)

    /** PRD §5.3 — a PUC test is same-day work, so the leads are shorter. */
    private val PUC_LEAD_DAYS = listOf(15, 3)

    /** Not in the PRD table; an RTO visit needs more notice than an online purchase. */
    private val LICENCE_LEAD_DAYS = listOf(30, 7)
}

/**
 * One scheduled nudge: [daysBefore] the document expires, which falls [on] this day.
 *
 * Carries both because the screen says both — the date is what the owner marks in their
 * head, the lead is what makes the date make sense.
 */
data class DocumentReminder(
    val daysBefore: Int,
    val on: LocalDate,
)
