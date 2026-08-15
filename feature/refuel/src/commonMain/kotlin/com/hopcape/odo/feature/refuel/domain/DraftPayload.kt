package com.hopcape.odo.feature.refuel.domain

import com.hopcape.odo.core.domain.cost.model.FuelFillDraft

/**
 * A draft as a single string, for the trip through a notification and back.
 *
 * A notification outlives the process that posted it. The owner pays at the pump, puts the
 * phone away, and taps Confirm at a red light ten minutes later, by which time the app may
 * have been killed — so the draft has to travel inside the intent rather than being looked up
 * from something held in memory.
 *
 * A plain delimited encoding rather than JSON: this crosses no version boundary and no
 * network, both ends are this file, and the values are five numbers and two short strings.
 * A serializer would be a dependency and a schema for something that never leaves the device.
 *
 * Decoding is total. Anything malformed — an intent from an older build, a truncated extra —
 * comes back `null`, and the caller simply does not log a fill. A crash while handling a
 * notification tap is a worse answer than a fill the owner has to enter themselves.
 */
internal object DraftPayload {

    fun encode(draft: FuelFillDraft): String = listOf(
        draft.source.name,
        draft.unit.name,
        draft.amount?.paise?.toString().orEmpty(),
        draft.amountOrigin.name,
        draft.quantityMilli?.toString().orEmpty(),
        draft.quantityOrigin.name,
        draft.pricePerUnit?.paise?.toString().orEmpty(),
        draft.priceOrigin.name,
        draft.odometerKm?.toString().orEmpty(),
        draft.odometerOrigin.name,
        // Last, and the only field that may contain anything: a merchant name can hold the
        // delimiter, and putting it at the end means it cannot shift the fields before it.
        draft.stationName.orEmpty(),
    ).joinToString(SEPARATOR)

    fun decode(payload: String): FuelFillDraft? {
        val parts = payload.split(SEPARATOR)
        if (parts.size < FIELD_COUNT) return null
        return runCatching {
            FuelFillDraft(
                source = enumValueOf(parts[0]),
                unit = enumValueOf(parts[1]),
                amount = parts[2].toAmount(),
                amountOrigin = enumValueOf(parts[3]),
                quantityMilli = parts[4].toLongOrNull(),
                quantityOrigin = enumValueOf(parts[5]),
                pricePerUnit = parts[6].toAmount(),
                priceOrigin = enumValueOf(parts[7]),
                odometerKm = parts[8].toIntOrNull(),
                odometerOrigin = enumValueOf(parts[9]),
                // Rejoined, because a merchant name containing the delimiter was split above.
                stationName = parts.drop(FIELD_COUNT - 1)
                    .joinToString(SEPARATOR)
                    .takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private fun String.toAmount() = toLongOrNull()
        ?.let { com.hopcape.odo.core.domain.shared.Amount.of(it).getOrNull() }

    private inline fun <reified T : Enum<T>> enumValueOf(name: String): T =
        enumValues<T>().first { it.name == name }

    /**
     * ASCII unit separator — a control character, so no merchant name, enum name or number can
     * contain it. A comma or a pipe could turn up in a station name and shift every field
     * after it.
     */
    private const val SEPARATOR = "\u001F"

    private const val FIELD_COUNT = 11
}
