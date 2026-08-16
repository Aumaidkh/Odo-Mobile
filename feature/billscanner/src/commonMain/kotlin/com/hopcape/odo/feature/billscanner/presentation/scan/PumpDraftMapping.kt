package com.hopcape.odo.feature.billscanner.presentation.scan

import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.scan.model.ExtractedPumpReading
import com.hopcape.odo.core.navigation.FuelFillDraftInput

/**
 * A pump reading on its way to the refuel confirm step.
 *
 * The handoff between two features that may not import each other. Both know
 * `:core:navigation`, so the draft crosses as its navigation type and refuel maps it back
 * into the domain on the other side.
 *
 * Every field that read is marked [FieldOrigin.OCR] rather than trusted silently. A camera
 * reading seven-segment digits is the least reliable input the app has, and the confirm step
 * has to be able to tell the owner which numbers came from it.
 */
internal fun ExtractedPumpReading.toDraftInput(): FuelFillDraftInput = FuelFillDraftInput(
    source = FillEntrySource.PUMP_OCR.name,
    amountPaise = amountPaise,
    amountOrigin = amountPaise.originName(),
    quantityMilli = quantityMilli,
    quantityOrigin = quantityMilli.originName(),
    pricePerUnitPaise = pricePerUnitPaise,
    priceOrigin = pricePerUnitPaise.originName(),
)

/**
 * A value that is absent has no origin to report.
 *
 * Saying `OCR` for a field the reader never produced would put a "read from the pump" chip
 * beside an empty box, which is the opposite of what the chips are for.
 */
private fun Long?.originName(): String =
    if (this == null) FieldOrigin.UNKNOWN.name else FieldOrigin.OCR.name
