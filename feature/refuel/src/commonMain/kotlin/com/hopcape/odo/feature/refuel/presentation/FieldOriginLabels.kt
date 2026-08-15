package com.hopcape.odo.feature.refuel.presentation

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_origin_derived
import com.hopcape.odo.feature.refuel.resources.rf_origin_history
import com.hopcape.odo.feature.refuel.resources.rf_origin_ocr
import com.hopcape.odo.feature.refuel.resources.rf_origin_payment
import com.hopcape.odo.feature.refuel.resources.rf_origin_predicted
import com.hopcape.odo.feature.refuel.resources.rf_source_detected
import com.hopcape.odo.feature.refuel.resources.rf_source_manual
import com.hopcape.odo.feature.refuel.resources.rf_source_prefilled
import com.hopcape.odo.feature.refuel.resources.rf_source_pump
import org.jetbrains.compose.resources.stringResource

/**
 * How a field's provenance and a capture channel are shown.
 *
 * Kept out of the screens because the confirm surface, the form and the success screen all
 * label the same things, and three copies of this mapping is three chances for "OCR" to mean
 * something different on one of them.
 */

/**
 * The chip next to a value, or `null` when there is nothing worth saying.
 *
 * A field the owner typed carries no chip: they know where it came from, and a label on
 * their own keystrokes is noise. `UNKNOWN` is likewise silent — an empty field explains
 * itself.
 */
@Composable
internal fun FieldOrigin.label(): String? = when (this) {
    FieldOrigin.PAYMENT -> stringResource(Res.string.rf_origin_payment)
    FieldOrigin.OCR -> stringResource(Res.string.rf_origin_ocr)
    FieldOrigin.HISTORY -> stringResource(Res.string.rf_origin_history)
    FieldOrigin.PREDICTED -> stringResource(Res.string.rf_origin_predicted)
    FieldOrigin.DERIVED -> stringResource(Res.string.rf_origin_derived)
    FieldOrigin.TYPED, FieldOrigin.UNKNOWN -> null
}

/**
 * The tone that chip is drawn in.
 *
 * A predicted value is the only one drawn as a warning. Everything else was observed by
 * something — a payment, a camera, the owner's own history — and only the projection is
 * Odo asserting a number nobody has seen.
 */
internal fun FieldOrigin.tone(): OdoBadgeTone = when (this) {
    FieldOrigin.PREDICTED -> OdoBadgeTone.Warning
    FieldOrigin.PAYMENT, FieldOrigin.OCR -> OdoBadgeTone.Success
    else -> OdoBadgeTone.Neutral
}

/** The badge naming which channel captured the fill. */
@Composable
internal fun FillEntrySource.label(): String = stringResource(
    when (this) {
        FillEntrySource.DETECTED -> Res.string.rf_source_detected
        FillEntrySource.PUMP_OCR -> Res.string.rf_source_pump
        FillEntrySource.PREFILLED -> Res.string.rf_source_prefilled
        FillEntrySource.MANUAL -> Res.string.rf_source_manual
    },
)
