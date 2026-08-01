package com.hopcape.odo.core.domain.alerts.model

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlinx.datetime.LocalDate

/**
 * The one thing about a car that needs the owner's attention today.
 *
 * Shared kernel rather than a feature's own type, because the same answer is rendered in
 * more than one place: Home shows it as its "needs attention" card, and the reminder engine
 * (M4) will decide what to push from the same rule. A feature may not import a feature, so
 * the answer belongs here next to
 * [AttentionPicker][com.hopcape.odo.core.domain.alerts.analysis.AttentionPicker], which
 * works it out.
 *
 * Deliberately one item and not a list. A dashboard card that lists four things is a to-do
 * list nobody reads; the picker ranks them and returns the one worth acting on first, and
 * the screen that wants everything (the vault, the ledger) already shows it.
 *
 * No copy here — the cases carry the numbers, and each surface writes its own line. Dates
 * and counts travel together because a card says both ("expired 3 days ago · 12 Jul").
 */
sealed interface CarAttention {

    /**
     * True when the deadline has already passed. What separates "renew now" from "plan
     * this", which is the difference between a warning tone and a neutral one.
     */
    val isOverdue: Boolean

    /**
     * A paper that has lapsed. Driving on lapsed insurance or a lapsed PUC is an offence in
     * India, which is why this outranks everything else.
     */
    data class DocumentLapsed(
        val documentId: DocumentId,
        val type: DocumentType,
        /** The day it stopped covering the owner. */
        val since: LocalDate,
        val daysAgo: Int,
    ) : CarAttention {
        override val isOverdue: Boolean get() = true
    }

    /** A paper inside its renewal window — still valid, but worth booking now. */
    data class DocumentExpiring(
        val documentId: DocumentId,
        val type: DocumentType,
        val until: LocalDate,
        val daysLeft: Int,
    ) : CarAttention {
        override val isOverdue: Boolean get() = false
    }

    /**
     * The service interval has passed on time, on distance, or both.
     *
     * [kmOverdue] is `null` when the car has no usable odometer pair, so a surface can say
     * "3 weeks overdue" without inventing a distance.
     */
    data class ServiceOverdue(
        val daysOverdue: Int,
        val kmOverdue: Int?,
    ) : CarAttention {
        override val isOverdue: Boolean get() = true
    }

    /** The service interval is close on time or distance — enough notice to book a slot. */
    data class ServiceDue(
        val daysLeft: Int,
        val kmLeft: Int?,
    ) : CarAttention {
        override val isOverdue: Boolean get() = false
    }
}
