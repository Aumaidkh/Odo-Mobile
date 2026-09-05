package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import com.hopcape.odo.web.admin.domain.BillingRepository
import com.hopcape.odo.web.admin.domain.BillingSummary
import com.hopcape.odo.web.admin.domain.CatalogueRepository
import com.hopcape.odo.web.admin.domain.ServiceItem
import com.hopcape.odo.web.admin.domain.Subscription
import com.hopcape.odo.web.admin.domain.Ticket
import com.hopcape.odo.web.admin.domain.TicketsRepository
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.Serializable

/** `service_categories` over PostgREST, behind `fairness.write`. */
internal class SupabaseCatalogueRepository(
    private val postgrest: Postgrest,
) : CatalogueRepository {

    override suspend fun items(): Either<WebError, List<ServiceItem>> =
        postgrest.select(
            table = TABLE,
            serializer = ItemRow.serializer(),
            query = "select=id,slug,display_name,interval_km,interval_months,benchmark_paise,notes,is_active" +
                "&order=display_name.asc",
        ).map { rows ->
            rows.map {
                ServiceItem(
                    id = it.id,
                    slug = it.slug,
                    name = it.displayName,
                    intervalKm = it.intervalKm,
                    intervalMonths = it.intervalMonths,
                    benchmarkPaise = it.benchmarkPaise,
                    notes = it.notes,
                    isActive = it.isActive,
                )
            }
        }

    override suspend fun save(
        id: String,
        name: String,
        intervalKm: Int?,
        intervalMonths: Int?,
        benchmarkPaise: Long?,
        notes: String?,
    ): Either<WebError, Unit> {
        // Nulls written explicitly. PostgREST reads an absent key as "leave this
        // column alone", so clearing an interval by emptying the field would keep
        // the old one — the same trap the app's Supabase adapters carry a note about.
        val body = buildString {
            append("""{"display_name":"${name.jsonEscaped()}",""")
            append(""""interval_km":${intervalKm ?: "null"},""")
            append(""""interval_months":${intervalMonths ?: "null"},""")
            append(""""benchmark_paise":${benchmarkPaise ?: "null"},""")
            append(""""notes":${notes?.let { "\"${it.jsonEscaped()}\"" } ?: "null"}}""")
        }
        return postgrest.patch(table = TABLE, query = "id=eq.$id", body = body)
    }

    override suspend fun setActive(id: String, active: Boolean): Either<WebError, Unit> =
        postgrest.patch(table = TABLE, query = "id=eq.$id", body = """{"is_active":$active}""")

    private companion object { const val TABLE = "service_categories" }
}

/** `support_tickets` over PostgREST, behind `users.read`. */
internal class SupabaseTicketsRepository(
    private val postgrest: Postgrest,
) : TicketsRepository {

    override suspend fun tickets(): Either<WebError, List<Ticket>> =
        postgrest.select(
            table = TABLE,
            serializer = TicketRow.serializer(),
            // Open first, then oldest: a queue is worked from the front, and the
            // person who wrote in three weeks ago has waited longest.
            // The app's own columns come too. Without them a report from the app reads as a
            // subject and a paragraph, and the area it was filed against — the thing that
            // routes it — is invisible.
            query = "select=id,contact,subject,body,status,priority,created_at," +
                "kind,details,attachments,diagnostics_reference" +
                "&order=status.asc,created_at.asc",
        ).map { rows ->
            rows.map {
                Ticket(
                    id = it.id,
                    contact = it.contact,
                    subject = it.subject,
                    body = it.body,
                    status = it.status,
                    priority = it.priority,
                    createdAt = it.createdAt.substringBefore('T'),
                    kind = it.kind.orEmpty(),
                    details = it.details.readDetails(),
                    attachments = it.attachments.readAttachmentNames(),
                    diagnosticsReference = it.diagnosticsReference,
                )
            }
        }

    override suspend fun setStatus(id: Long, status: String): Either<WebError, Unit> {
        // resolved_at travels with the status, or a reopened ticket keeps the date
        // it was closed and every "time to resolve" reading is wrong afterwards.
        val resolvedAt = if (status == "resolved" || status == "closed") "\"now()\"" else "null"
        return postgrest.patch(
            table = TABLE,
            query = "id=eq.$id",
            body = """{"status":"$status","resolved_at":$resolvedAt}""",
        )
    }

    override suspend fun setPriority(id: Long, priority: String): Either<WebError, Unit> =
        postgrest.patch(table = TABLE, query = "id=eq.$id", body = """{"priority":"$priority"}""")

    private companion object { const val TABLE = "support_tickets" }
}

/** `subscriptions` over PostgREST. Read-only by design — see [BillingRepository]. */
internal class SupabaseBillingRepository(
    private val postgrest: Postgrest,
) : BillingRepository {

    override suspend fun subscriptions(): Either<WebError, List<Subscription>> =
        postgrest.select(
            table = "subscriptions",
            serializer = SubscriptionRow.serializer(),
            // The owner's phone comes back embedded rather than as a request per
            // row — a page of twenty would otherwise be twenty-one round trips.
            query = "select=id,owner_id,tier,status,current_period_end,created_at,profiles(phone)" +
                "&order=current_period_end.asc",
        ).map { rows ->
            rows.map {
                Subscription(
                    id = it.id,
                    ownerId = it.ownerId,
                    ownerContact = it.owner?.phone,
                    tier = it.tier,
                    status = it.status,
                    renewsOn = it.currentPeriodEnd?.substringBefore('T'),
                    startedOn = it.createdAt.substringBefore('T'),
                )
            }
        }

    override suspend fun summary(): Either<WebError, BillingSummary> =
        postgrest.rpcOne(
            name = "admin_billing_summary",
            body = "{}",
            serializer = SummaryRow.serializer(),
        ).map {
            BillingSummary(
                total = it?.total ?: 0,
                active = it?.active ?: 0,
                pastDue = it?.pastDue ?: 0,
                cancelled = it?.cancelled ?: 0,
                renewing30d = it?.renewing30d ?: 0,
            )
        }
}

@Serializable
private data class ItemRow(
    val id: String,
    val slug: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("interval_km") val intervalKm: Int? = null,
    @SerialName("interval_months") val intervalMonths: Int? = null,
    @SerialName("benchmark_paise") val benchmarkPaise: Long? = null,
    val notes: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
private data class TicketRow(
    val id: Long,
    val contact: String,
    val subject: String,
    val body: String = "",
    val status: String,
    val priority: String,
    @SerialName("created_at") val createdAt: String,
    /** Null on a ticket filed before the app could write here. */
    val kind: String? = null,
    val details: JsonElement? = null,
    val attachments: JsonElement? = null,
    @SerialName("diagnostics_reference") val diagnosticsReference: String? = null,
)

/**
 * The named values the form collected.
 *
 * Read defensively: the column is written by an app that ships ahead of this panel, so a
 * shape nobody here expected costs the extra fields on one ticket rather than the queue.
 */
private fun JsonElement?.readDetails(): Map<String, String> =
    (this as? JsonObject)?.mapNotNull { (key, value) ->
        (value as? JsonPrimitive)?.content?.let { key to it }
    }?.toMap().orEmpty()

/** The file names, for a list that says what came with a report. */
private fun JsonElement?.readAttachmentNames(): List<String> =
    (this as? JsonArray)?.mapNotNull { entry ->
        ((entry as? JsonObject)?.get("name") as? JsonPrimitive)?.content
    }.orEmpty()

@Serializable
private data class SubscriptionRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val tier: String,
    val status: String,
    @SerialName("current_period_end") val currentPeriodEnd: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("profiles") val owner: OwnerPhone? = null,
)

@Serializable
private data class OwnerPhone(val phone: String? = null)

@Serializable
private data class SummaryRow(
    val total: Int = 0,
    val active: Int = 0,
    @SerialName("past_due") val pastDue: Int = 0,
    val cancelled: Int = 0,
    @SerialName("renewing_30d") val renewing30d: Int = 0,
)
