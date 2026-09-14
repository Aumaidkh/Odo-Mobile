package com.hopcape.odo.core.domain.support

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Instant

/** What a submission is. One queue to the panel; three things only in what they collect. */
enum class TicketKind {
    PROBLEM,
    IDEA,
    PRICE_CORRECTION,
}

/** A file the owner attached, already copied into app storage. */
data class TicketAttachment(
    /** The key the file is stored under, never a picker reference — those stop resolving. */
    val storageKey: String,
    val name: String,
)

/**
 * Something the owner sent support, as it exists on the device.
 *
 * **Named here rather than by the server.** The id is generated on the device so a report
 * written with no signal is a real row the moment it is written, and the reference the owner
 * is shown is derived from that id — both survive a flight-mode week and a reinstall of the
 * app's process.
 *
 * The body is the owner's own words. Everything the form collected in a field of its own —
 * the area, what they paid, what they say is off — is carried in [details] as named values,
 * so the panel can route and filter without parsing prose.
 */
data class SupportTicket(
    val id: SupportTicketId,
    val kind: TicketKind,
    val body: String,
    /** Structured fields the form collected, keyed by name. Never the owner's prose. */
    val details: Map<String, String>,
    val attachments: List<TicketAttachment>,
    /** Where the answer goes. Null for a submission that is not answered — a correction. */
    val replyTo: String?,
    /** Set when the owner asked for logs to travel with it. */
    val diagnosticsReference: String?,
    val createdAt: Instant,
) {
    /** The code the owner is shown and quotes back. Derived, never stored twice. */
    val reference: String get() = TicketReference.of(id)

    companion object {

        /** Longer than anybody types, and short enough that a runaway paste is refused. */
        const val MAX_BODY_LENGTH = 5_000

        /**
         * A ticket, or the reason it is not one.
         *
         * The body is the only thing checked. A submission with nothing written in it is not
         * a submission — support would receive a row saying an area and nothing else — and
         * every other field is either optional or chosen from a fixed set on screen.
         */
        fun create(
            id: SupportTicketId,
            kind: TicketKind,
            body: String,
            createdAt: Instant,
            details: Map<String, String> = emptyMap(),
            attachments: List<TicketAttachment> = emptyList(),
            replyTo: String? = null,
            diagnosticsReference: String? = null,
        ): Either<DomainError, SupportTicket> {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return DomainError.EmptyTicketBody.left()
            if (trimmed.length > MAX_BODY_LENGTH) {
                return DomainError.TicketBodyTooLong(MAX_BODY_LENGTH).left()
            }
            return SupportTicket(
                id = id,
                kind = kind,
                body = trimmed,
                details = details,
                attachments = attachments,
                replyTo = replyTo?.trim()?.takeIf { it.isNotEmpty() },
                diagnosticsReference = diagnosticsReference,
                createdAt = createdAt,
            ).right()
        }
    }
}

/** The names [SupportTicket.details] uses, so the app and the panel agree on one spelling. */
object TicketDetail {
    const val AREA = "area"
    const val JOB = "job"
    const val COMPLAINT = "complaint"
    const val PAID_PAISE = "paid_paise"
    const val BAND_LOW_PAISE = "band_low_paise"
    const val BAND_HIGH_PAISE = "band_high_paise"
    const val CITY = "city"
    const val WORKSHOP_TIER = "workshop_tier"
    const val SEGMENT = "segment"
}
