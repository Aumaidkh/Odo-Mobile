package com.hopcape.odo.core.data.servicelog

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * The stored form of a [FairnessSnapshot] — what goes in `service_logs.fairness_snapshot`.
 *
 * **The verdict is deliberately not stored.** A verdict is a pure function of what was paid
 * and the benchmark it was compared against, so freezing the *estimate* freezes the verdict
 * with it. Storing both would create two versions of one fact that a future change to the
 * tolerance band could silently split apart — and it would mean serialising
 * `FairnessVerdict`, a sealed hierarchy, for no gain. Rebuilding through
 * [FairnessReport.of] guarantees a read-back report is one the domain would produce today.
 *
 * The DTO lives here rather than on the domain model because `@Serializable` is a framework
 * annotation and the shared kernel stays free of them — and because `Amount`'s constructor
 * is private, which is exactly the invariant we want kept.
 */
@Serializable
internal data class FairnessSnapshotDto(
    @SerialName("city") val city: String,
    @SerialName("checked_at") val checkedAt: String,
    @SerialName("items") val items: List<FairnessItemDto>,
)

@Serializable
internal data class FairnessItemDto(
    @SerialName("label") val label: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("amount_paise") val amountPaise: Long,
    /** The benchmark as it stood at check time; absent when the line was never judged. */
    @SerialName("city_average_paise") val cityAveragePaise: Long? = null,
    @SerialName("sample_size") val sampleSize: Int? = null,
)

/** Lenient about unknown keys so a payload written by a newer build still reads. */
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

internal fun FairnessSnapshot.toJson(): String = json.encodeToString(
    FairnessSnapshotDto(
        city = report.city,
        checkedAt = checkedAt.toString(),
        items = report.items.map { item ->
            FairnessItemDto(
                label = item.label,
                category = item.category?.name,
                amountPaise = item.amount.paise,
                cityAveragePaise = item.estimate?.cityAverage?.paise,
                sampleSize = item.estimate?.sampleSize,
            )
        },
    ),
)

/**
 * Rebuild a snapshot from its stored JSON, or `null` if the column is empty or unreadable.
 *
 * Unreadable is treated as absent rather than fatal: a fairness verdict is derived
 * decoration on an entry, and losing it must never make the entry itself unopenable. An
 * unrecognised category is dropped the same way — the line stays, simply unjudged.
 */
internal fun String?.toFairnessSnapshot(): FairnessSnapshot? {
    val dto = runCatching { this?.let { json.decodeFromString<FairnessSnapshotDto>(it) } }.getOrNull() ?: return null
    val checkedAt = runCatching { Instant.parse(dto.checkedAt) }.getOrNull() ?: return null

    val categories = dto.items.associate { item ->
        item to item.category?.let { name -> ServiceCategory.entries.firstOrNull { it.name == name } }
    }

    val query = FairnessQuery(
        city = dto.city,
        items = dto.items.map { item ->
            FairnessQueryItem(label = item.label, category = categories[item], amount = paise(item.amountPaise))
        },
    )

    // One estimate per category, exactly as the benchmark table is keyed.
    val estimates = dto.items.mapNotNull { item ->
        val category = categories[item] ?: return@mapNotNull null
        val average = item.cityAveragePaise ?: return@mapNotNull null
        category to FairnessEstimate(
            category = category,
            city = dto.city,
            cityAverage = paise(average),
            sampleSize = item.sampleSize ?: 0,
        )
    }.toMap()

    return FairnessSnapshot(report = FairnessReport.of(query, estimates), checkedAt = checkedAt)
}

private fun paise(value: Long): Amount = Amount.of(value).getOrElse { Amount.ZERO }
