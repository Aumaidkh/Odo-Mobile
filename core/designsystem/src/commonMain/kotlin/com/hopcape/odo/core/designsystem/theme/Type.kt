package com.hopcape.odo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Odo's typeface. The spec is **Inter** across the board. Until the Inter `.ttf`
 * files are added to `commonMain/composeResources/font/`, this resolves to the
 * platform default so the module builds and the *scale* is already correct.
 *
 * To switch on Inter: drop the weights in, then replace the body with a
 * `FontFamily(Font(Res.font.inter_regular, FontWeight.Normal), …)` builder.
 * Nothing else changes — every style below reads from this one constant.
 */
val OdoFontFamily: FontFamily = FontFamily.Default

/**
 * Odo's type scale — the named roles from the design spec. Screens read these
 * (`OdoTheme.typography.display`) instead of Material's generic slots, so intent
 * stays explicit. Sizes/weights/line-heights are verbatim from the spec.
 *
 * @property display   52/700/1.0, -1.5% — health score & per-km hero numbers
 * @property title     24/700/1.1, -1%  — screen & section titles
 * @property heading   18/600/1.2        — card & in-screen headers
 * @property numeric   600 · tabular figures — money, km, score (resize per use)
 * @property body      16/400/1.5         — default running copy
 * @property bodySmall 14/400/1.5         — secondary text & helper copy
 * @property label     14/500/1.5         — buttons & field labels
 * @property caption   12/600, +.12em     — confidence, tags, eyebrows (UPPERCASE at call site)
 */
@Immutable
data class OdoTypography(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val numeric: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

val OdoDefaultTypography: OdoTypography = OdoTypography(
    display = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 52.sp,
        lineHeight = 52.sp, // 1.0
        letterSpacing = (-0.015).em, // -1.5%
    ),
    title = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 24.sp,
        lineHeight = 26.4.sp, // 1.1
        letterSpacing = (-0.01).em, // -1%
    ),
    heading = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 18.sp,
        lineHeight = 21.6.sp, // 1.2
    ),
    numeric = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 20.sp, // base of the 16–28 range; `.copy(fontSize = …)` per use
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum", // tabular figures so columns of money/km align
    ),
    body = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 16.sp,
        lineHeight = 24.sp, // 1.5
    ),
    bodySmall = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 14.sp,
        lineHeight = 21.sp, // 1.5
    ),
    label = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    caption = TextStyle(
        fontFamily = OdoFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.12.em, // +.12em tracked caps
    ),
)

internal val LocalOdoTypography = staticCompositionLocalOf { OdoDefaultTypography }

/**
 * Material 3 [Typography] mapped from the Odo scale, so stock Material components
 * pick up the brand type. Bespoke screens should prefer [OdoTheme.typography].
 */
internal fun odoMaterialTypography(t: OdoTypography): Typography = Typography(
    displayLarge = t.display,
    displayMedium = t.display.copy(fontSize = 40.sp, lineHeight = 44.sp),
    headlineLarge = t.title,
    headlineMedium = t.title.copy(fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = t.title.copy(fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = t.heading,
    titleSmall = t.heading.copy(fontSize = 16.sp, lineHeight = 20.sp),
    bodyLarge = t.body,
    bodyMedium = t.bodySmall,
    bodySmall = t.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = t.label,
    labelMedium = t.caption.copy(letterSpacing = 0.05.em),
    labelSmall = t.caption,
)
