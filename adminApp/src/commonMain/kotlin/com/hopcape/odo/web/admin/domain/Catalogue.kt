package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/**
 * One service item: how often it is due, and what it ought to cost.
 *
 * Both intervals may be set and either may be null. A service due "every 10,000 km
 * or 12 months" is one rule, and a model that could hold only one of them would
 * force every such item to lie about the other.
 *
 * [benchmarkPaise] is a reference figure the fairness verdict reads — not a price
 * anybody is charged.
 */
data class ServiceItem(
    val id: String,
    val slug: String,
    val name: String,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    val benchmarkPaise: Long?,
    val notes: String?,
    val isActive: Boolean,
) {
    /** Rupees, for display. Paise everywhere else, because a divided float drifts. */
    val benchmarkRupees: Long? get() = benchmarkPaise?.let { it / 100 }
}

interface CatalogueRepository {
    suspend fun items(): Either<WebError, List<ServiceItem>>

    suspend fun save(
        id: String,
        name: String,
        intervalKm: Int?,
        intervalMonths: Int?,
        benchmarkPaise: Long?,
        notes: String?,
    ): Either<WebError, Unit>

    /** Retire or restore. The app's picker reads `is_active`. */
    suspend fun setActive(id: String, active: Boolean): Either<WebError, Unit>
}

/** One support ticket. */
data class Ticket(
    val id: Long,
    val contact: String,
    val subject: String,
    val body: String,
    val status: String,
    val priority: String,
    val createdAt: String,
    /**
     * Which form it came from — `PROBLEM`, `IDEA`, `PRICE_CORRECTION` — or blank for a ticket
     * that predates the app writing here.
     */
    val kind: String = "",
    /**
     * What the form collected in fields of its own: the area for a report, the job and the
     * figure for a price correction. Named values rather than prose, which is what lets this
     * queue be worked without reading every body.
     */
    val details: Map<String, String> = emptyMap(),
    /** The file names attached. The files themselves are in storage. */
    val attachments: List<String> = emptyList(),
    /**
     * The diagnostics upload that travelled with it.
     *
     * The single most useful thing on a bug report: it is the code the uploaded logs are filed
     * under, so whoever works the ticket can find them without asking the owner for anything.
     */
    val diagnosticsReference: String? = null,
) {
    val isOpen: Boolean get() = status == "open" || status == "pending"

    /** True for a ticket the app filed, which is the only kind carrying the fields above. */
    val isFromApp: Boolean get() = kind.isNotBlank()
}

interface TicketsRepository {
    suspend fun tickets(): Either<WebError, List<Ticket>>
    suspend fun setStatus(id: Long, status: String): Either<WebError, Unit>
    suspend fun setPriority(id: Long, priority: String): Either<WebError, Unit>
}

/** One subscription, as billing shows it. */
data class Subscription(
    val id: String,
    val ownerId: String,
    val ownerContact: String?,
    val tier: String,
    val status: String,
    val renewsOn: String?,
    val startedOn: String,
)

/** The counts the header draws, summed in the database rather than over one page. */
data class BillingSummary(
    val total: Int,
    val active: Int,
    val pastDue: Int,
    val cancelled: Int,
    val renewing30d: Int,
)

/**
 * Billing, read-only.
 *
 * A subscription's truth lives with the store; an admin editing a row here would
 * produce a figure that disagrees with what the owner was actually charged.
 * Comping somebody is `entitlement_overrides`, which is a different thing said in
 * a different table.
 */
interface BillingRepository {
    suspend fun subscriptions(): Either<WebError, List<Subscription>>
    suspend fun summary(): Either<WebError, BillingSummary>
}
