package com.hopcape.odo.feature.refuel.domain

import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.navigation.FuelFillDraftInput

/**
 * The boundary between the navigation key and the domain draft.
 *
 * A back-stack key holds primitives — `:core:navigation` may not depend on `:core:domain` —
 * so every capture channel hands its draft over as a [FuelFillDraftInput] and the confirm
 * step turns it back. The conversion lives here, in one file, rather than in each route:
 * a second copy of it is how the two sides start disagreeing about what an absent field
 * means.
 *
 * Unrecognised enum names read as their safest value rather than throwing. A key can outlive
 * the build that wrote it — the process is killed, the owner returns hours later, and the app
 * has been updated in between — and a crash while restoring a back stack is a worse answer
 * than a fill that has to be confirmed by hand.
 */
internal fun FuelFillDraftInput.toDomain(): FuelFillDraft = FuelFillDraft(
    source = FillEntrySource.entries.firstOrNull { it.name == source } ?: FillEntrySource.MANUAL,
    amount = amountPaise.toAmount(),
    amountOrigin = amountOrigin.toOrigin(),
    quantityMilli = quantityMilli,
    quantityOrigin = quantityOrigin.toOrigin(),
    pricePerUnit = pricePerUnitPaise.toAmount(),
    priceOrigin = priceOrigin.toOrigin(),
    odometerKm = odometerKm,
    odometerOrigin = odometerOrigin.toOrigin(),
    stationName = stationName,
    transactionRef = transactionRef,
)

/** The same trip in reverse, for a channel that has built a draft and wants to navigate. */
internal fun FuelFillDraft.toInput(): FuelFillDraftInput = FuelFillDraftInput(
    source = source.name,
    amountPaise = amount?.paise,
    amountOrigin = amountOrigin.name,
    quantityMilli = quantityMilli,
    quantityOrigin = quantityOrigin.name,
    pricePerUnitPaise = pricePerUnit?.paise,
    priceOrigin = priceOrigin.name,
    odometerKm = odometerKm,
    odometerOrigin = odometerOrigin.name,
    stationName = stationName,
    transactionRef = transactionRef,
)

/**
 * A negative amount in a key is dropped rather than clamped to zero.
 *
 * Zero is a real value here — the confirm step shows "₹0" and asks the owner to correct it —
 * so turning corruption into zero would present a made-up number as though a channel had
 * read it.
 */
private fun Long?.toAmount(): Amount? = this?.let { Amount.of(it).getOrNull() }

private fun String?.toOrigin(): FieldOrigin =
    FieldOrigin.entries.firstOrNull { it.name == this } ?: FieldOrigin.UNKNOWN
