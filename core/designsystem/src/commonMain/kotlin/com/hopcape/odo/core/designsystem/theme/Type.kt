package com.hopcape.odo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.resources.Res
import com.hopcape.odo.core.designsystem.resources.dm_sans_bold
import com.hopcape.odo.core.designsystem.resources.dm_sans_extra_bold
import com.hopcape.odo.core.designsystem.resources.dm_sans_light
import com.hopcape.odo.core.designsystem.resources.dm_sans_medium
import com.hopcape.odo.core.designsystem.resources.dm_sans_regular
import com.hopcape.odo.core.designsystem.resources.dm_sans_semi_bold
import org.jetbrains.compose.resources.Font

/**
 * Odo's typeface: **DM Sans**, loaded from the `.ttf` weights (300–800) in
 * `commonMain/composeResources/font/`. A `@Composable` getter because Compose
 * Multiplatform's `Font(FontResource, …)` must resolve inside a composition;
 * [OdoTheme] reads it once and every style flows from there.
 */
val OdoFontFamily: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.dm_sans_light, FontWeight.W300),
        Font(Res.font.dm_sans_regular, FontWeight.W400),
        Font(Res.font.dm_sans_medium, FontWeight.W500),
        Font(Res.font.dm_sans_semi_bold, FontWeight.W600),
        Font(Res.font.dm_sans_bold, FontWeight.W700),
        Font(Res.font.dm_sans_extra_bold, FontWeight.W800),
    )

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

/**
 * The Odo scale built on [fontFamily]. [OdoTheme] calls this with [OdoFontFamily]
 * (DM Sans); [OdoDefaultTypography] calls it with the platform default so the
 * CompositionLocal has a resource-free fallback outside the theme.
 */
fun odoTypography(fontFamily: FontFamily): OdoTypography = OdoTypography(
    display = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 52.sp,
        lineHeight = 52.sp, // 1.0
        letterSpacing = (-0.015).em, // -1.5%
    ),
    title = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 24.sp,
        lineHeight = 26.4.sp, // 1.1
        letterSpacing = (-0.01).em, // -1%
    ),
    heading = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal, // 600
        fontSize = 18.sp,
        lineHeight = 21.6.sp, // 1.2
    ),
    numeric = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 20.sp, // base of the 16–28 range; `.copy(fontSize = …)` per use
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum", // tabular figures so columns of money/km align
    ),
    body = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 16.sp,
        lineHeight = 24.sp, // 1.5
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 14.sp,
        lineHeight = 21.sp, // 1.5
    ),
    label = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    caption = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.12.em, // +.12em tracked caps
    ),
)

val OdoDefaultTypography: OdoTypography = odoTypography(FontFamily.Default)

internal val LocalOdoTypography = staticCompositionLocalOf { OdoDefaultTypography }

/**
 * How much bigger "larger text" makes everything.
 *
 * 15% is enough to be an obvious improvement for someone squinting at a bill total, and
 * small enough that the odometer drum, the badges and the two-across cards still lay out.
 * It multiplies the device's own font scale rather than replacing it.
 */
const val OdoLargerTextScale: Float = 1.15f

/**
 * Every style at [factor] times its size. Line heights scale with the sizes, so the
 * spacing between lines stays proportional instead of tightening as the text grows.
 */
fun OdoTypography.scaledBy(factor: Float): OdoTypography =
    if (factor == 1f) {
        this
    } else {
        OdoTypography(
            display = display.scaledBy(factor),
            title = title.scaledBy(factor),
            heading = heading.scaledBy(factor),
            numeric = numeric.scaledBy(factor),
            body = body.scaledBy(factor),
            bodySmall = bodySmall.scaledBy(factor),
            label = label.scaledBy(factor),
            caption = caption.scaledBy(factor),
        )
    }

private fun TextStyle.scaledBy(factor: Float): TextStyle =
    copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

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
