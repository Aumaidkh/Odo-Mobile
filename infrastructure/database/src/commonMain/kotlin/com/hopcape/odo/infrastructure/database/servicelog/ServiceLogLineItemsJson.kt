package com.hopcape.odo.infrastructure.database.servicelog

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The stored form of an entry's priced breakdown — what goes in `service_logs.line_items`.
 *
 * A JSON column rather than a child table, the same call `fairness_snapshot` made. These
 * lines are a frozen record of what the bill said: nothing filters, sorts or aggregates by
 * them locally, they are always read with their parent, and their order is the order they
 * were printed in. A junction table would buy an independent identity none of them has, and
 * the `GROUP_CONCAT` trick the categories use cannot work here — a label is the workshop's
 * own wording, so it may well contain a comma.
 *
 * The DTO lives here rather than on the domain model because `@Serializable` is a framework
 * annotation and the shared kernel stays free of them — and because `Amount`'s constructor is
 * private, which is exactly the invariant we want kept.
 */
@Serializable
internal data class ServiceLogLineItemDto(
    /** The workshop's own wording, kept as written. Null when the bill gave none. */
    @SerialName("label") val label: String? = null,
    @SerialName("category") val category: String,
    @SerialName("amount_paise") val amountPaise: Long,
)

/** Lenient about unknown keys so a payload written by a newer build still reads. */
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/** Null for an entry with no breakdown, so the column stays empty rather than holding `[]`. */
internal fun List<ServiceLogLineItem>.toJson(): String? =
    if (isEmpty()) {
        null
    } else {
        json.encodeToString(
            map { item ->
                ServiceLogLineItemDto(
                    label = item.label,
                    category = item.category.name,
                    amountPaise = item.amount.paise,
                )
            },
        )
    }

/**
 * Rebuild the breakdown from its stored JSON, in the order it was written.
 *
 * Unreadable is treated as absent rather than fatal, and a single bad line is dropped rather
 * than taking the rest with it. The breakdown is detail on an entry whose total is the
 * authoritative figure, so losing it must never make the entry itself unopenable — the same
 * rule the fairness snapshot follows. A category name this build does not know reads as
 * [ServiceCategory.OTHER]: the amount and the label are what the owner is looking at, and
 * dropping the line over a taxonomy the paper never mentioned would lose more than it saves.
 */
internal fun String?.toLineItems(): List<ServiceLogLineItem> {
    val dtos = runCatching {
        this?.let { json.decodeFromString<List<ServiceLogLineItemDto>>(it) }
    }.getOrNull() ?: return emptyList()

    return dtos.mapNotNull { dto ->
        ServiceLogLineItem.of(
            label = dto.label,
            category = ServiceCategory.entries.firstOrNull { it.name == dto.category }
                ?: ServiceCategory.OTHER,
            amountPaise = dto.amountPaise,
        ).getOrElse { null }
    }
}
