package com.hopcape.odo.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Odo's corner-radius tokens (the SHAPE scale from the spec). Named by use so
 * call sites read intent: `OdoTheme.shapes.card`, `OdoTheme.shapes.pill`.
 *
 * @property small  10dp — chips, small controls (`radius-sm`)
 * @property field  12dp — text fields, inputs (`radius-field`)
 * @property card   16dp — cards, panels (`radius-card`)
 * @property pill   fully rounded — buttons, badges, tags (`radius-pill`, 999)
 * @property device 34dp — device-frame / hero containers (`radius-device`)
 */
@Immutable
data class OdoShapes(
    val small: RoundedCornerShape,
    val field: RoundedCornerShape,
    val card: RoundedCornerShape,
    val pill: RoundedCornerShape,
    val device: RoundedCornerShape,
)

val OdoDefaultShapes: OdoShapes = OdoShapes(
    small = RoundedCornerShape(10.dp),
    field = RoundedCornerShape(12.dp),
    card = RoundedCornerShape(16.dp),
    pill = RoundedCornerShape(percent = 50), // 999 → fully rounded
    device = RoundedCornerShape(34.dp),
)

internal val LocalOdoShapes = staticCompositionLocalOf { OdoDefaultShapes }

/** Material 3 [Shapes] mapped from the Odo radii, for stock Material components. */
internal fun odoMaterialShapes(s: OdoShapes): Shapes = Shapes(
    extraSmall = s.small,
    small = s.field,
    medium = s.card,
    large = s.card,
    extraLarge = s.device,
)
