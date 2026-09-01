package com.hopcape.odo.web.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One theme's worth of colour.
 *
 * A data class with two instances rather than two objects, so [AdminTokens] can read
 * whichever is in scope without every screen learning that a choice exists. The
 * names are ordered by how far forward a surface sits: [canvas] is the page, [rail]
 * and [card] are raised off it, [rowHover] is a row under the pointer.
 */
@Immutable
data class AdminPalette(
    val canvas: Color,
    val rail: Color,
    val card: Color,
    val tableHeader: Color,
    val field: Color,
    val railHover: Color,
    val rowHover: Color,
    val railSelected: Color,
    val hairline: Color,
    val railBorder: Color,
    val border: Color,
    val borderHover: Color,
    val text: Color,
    val textStrong: Color,
    val textMuted: Color,
    val textFaint: Color,
    val textDim: Color,
    val accent: Color,
    val accentWash: Color,
    val danger: Color,

    /**
     * The tight, darker layer directly under a raised surface — the contact shadow.
     *
     * Two colours rather than one alpha, because a shadow that reads as designed is
     * two layers: a small, relatively opaque one that anchors the surface to the
     * page, and a wide, very light one that suggests the room it is in. A single
     * blur at one alpha is what "default shadow" looks like.
     *
     * Neither is pure black. Black shadows go muddy against a coloured ground and
     * grey out whatever is under them; a slightly blue-black keeps the page feeling
     * lit rather than dirty.
     */
    val shadowKey: Color,

    /** The wide, soft layer — the ambient half of the pair. */
    val shadowAmbient: Color,

    /**
     * The light along a raised surface's top edge.
     *
     * This is what does the lifting in the dark theme, where a shadow cannot: a
     * #0A0A0A card on a #000000 page casts nothing visible however hard it is
     * thrown, but a one-pixel lighter edge along the top reads immediately as a
     * surface catching light from above. Transparent in the light theme, where the
     * shadow is doing the work and a highlight would just look like a seam.
     */
    val edgeHighlight: Color,

    val isDark: Boolean,
)

/**
 * The original design: a black canvas, hairline borders, a dozen exact greys.
 *
 * The depth here is almost entirely in the borders and the top-edge highlight — a
 * shadow on a #0A0A0A card over a #000000 page is invisible however hard it is
 * thrown, so [AdminPalette.edgeHighlight] does the lifting and the shadows only
 * deepen the space around an overlay.
 */
val DarkPalette = AdminPalette(
    canvas = Color(0xFF000000),
    rail = Color(0xFF0A0A0A),
    card = Color(0xFF0A0A0A),
    tableHeader = Color(0xFF0D0D0D),
    field = Color(0xFF0F0F0F),
    railHover = Color(0xFF161616),
    rowHover = Color(0xFF0F0F0F),
    railSelected = Color(0xFF181818),
    hairline = Color(0xFF141414),
    railBorder = Color(0xFF1A1A1A),
    border = Color(0xFF262626),
    borderHover = Color(0xFF3A3A3A),
    text = Color(0xFFFFFFFF),
    textStrong = Color(0xFFD1D5DB),
    textMuted = Color(0xFF9CA3AF),
    textFaint = Color(0xFF6B7280),
    textDim = Color(0xFF4B5563),
    accent = Color(0xFFD97706),
    accentWash = Color(0xFF1F1608),
    danger = Color(0xFFB91C1C),
    // Deep and nearly opaque, because on black only the very edge of a blur is
    // distinguishable at all — a lighter shadow here is simply not there.
    shadowKey = Color(0xFF000000).copy(alpha = 0.72f),
    shadowAmbient = Color(0xFF000000).copy(alpha = 0.48f),
    edgeHighlight = Color(0xFFFFFFFF).copy(alpha = 0.055f),
    isDark = true,
)

/**
 * The same design in daylight.
 *
 * Not an inversion. Inverting the greys gives near-black text on near-white and a
 * page that glares; this keeps the canvas slightly off-white so a pure-white card
 * still reads as raised, which is the one relationship the dark theme gets from
 * `#000` under `#0A0A0A`. Borders lighten but stay hairlines, because the layout
 * depends on them for structure.
 *
 * The accent and the danger red are darkened rather than reused: amber that reads as
 * a warning on black is illegible on white.
 */
val LightPalette = AdminPalette(
    canvas = Color(0xFFF4F4F5),
    rail = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    tableHeader = Color(0xFFFAFAFA),
    field = Color(0xFFF4F4F5),
    railHover = Color(0xFFF0F0F1),
    rowHover = Color(0xFFFAFAFA),
    railSelected = Color(0xFFEBEBED),
    hairline = Color(0xFFEDEDEF),
    railBorder = Color(0xFFE4E4E7),
    border = Color(0xFFD4D4D8),
    borderHover = Color(0xFFA1A1AA),
    text = Color(0xFF09090B),
    textStrong = Color(0xFF27272A),
    textMuted = Color(0xFF52525B),
    textFaint = Color(0xFF71717A),
    textDim = Color(0xFF9CA3AF),
    accent = Color(0xFFB45309),
    accentWash = Color(0xFFFEF3C7),
    danger = Color(0xFFB91C1C),
    // Slate rather than black: #0F172A under a white card reads as shadow, while
    // pure black at the same alpha reads as grime.
    shadowKey = Color(0xFF0F172A).copy(alpha = 0.10f),
    shadowAmbient = Color(0xFF0F172A).copy(alpha = 0.06f),
    edgeHighlight = Color.Transparent,
    isDark = false,
)

/**
 * Which palette is in scope.
 *
 * `static` rather than the ordinary kind: the theme changes when somebody flips a
 * switch and not otherwise, so the read does not need to be tracked per call site —
 * and there are 266 of them.
 */
val LocalAdminPalette = staticCompositionLocalOf { DarkPalette }

/**
 * The panel's design tokens.
 *
 * Still an object, and still read as `AdminTokens.text`, so no screen has to know
 * that there are two themes. The properties are `@Composable get()` over
 * [LocalAdminPalette] rather than constants — which is the whole reason adding a
 * light theme did not mean touching every screen.
 *
 * A token object rather than a Material `ColorScheme` because almost nothing here is
 * a Material surface: the design is a canvas with hairline borders and a dozen exact
 * greys, and expressing that as `surfaceVariant`/`outline` would mean every screen
 * guessing which Material role a given hex was standing in for. [MaterialTheme] is
 * still applied underneath, for the handful of components that read from it.
 */
object AdminTokens {

    /** The page. */
    val canvas: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.canvas

    /** The rail, and every card. */
    val rail: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.rail
    val card: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.card

    /** A table's own header band, one step off the card it sits in. */
    val tableHeader: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.tableHeader

    /** Inputs and pills. */
    val field: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.field

    /** The nav item under the pointer, and a table row under it. */
    val railHover: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.railHover
    val rowHover: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.rowHover

    /** The selected nav item. */
    val railSelected: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.railSelected

    /** Structure. [hairline] separates rows; [border] outlines a card or a control. */
    val hairline: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.hairline
    val railBorder: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.railBorder
    val border: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.border
    val borderHover: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.borderHover

    /** Text, brightest first. */
    val text: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.text
    val textStrong: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.textStrong
    val textMuted: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.textMuted
    val textFaint: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.textFaint
    val textDim: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.textDim

    /**
     * The one accent, used sparingly and only to mean "look at this".
     *
     * Amber rather than a brand colour: this is an internal tool where the only
     * thing worth colouring is a thing that needs a decision.
     */
    val accent: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.accent

    /** The wash behind an attention row. */
    val accentWash: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.accentWash

    val danger: Color @Composable @ReadOnlyComposable get() = LocalAdminPalette.current.danger

    /**
     * The system stack rather than Inter.
     *
     * The mockup specifies Inter, and a Compose canvas cannot use the browser's
     * webfont — it would have to be bundled into the Wasm module as a resource,
     * which is a few hundred KB on a page opened by a handful of people. The
     * metrics are close enough that the layout is unchanged; this is the one
     * deliberate departure from the design.
     *
     * A plain `val`, unlike the colours: [AdminType] is a non-composable object and
     * builds its styles once.
     */
    val fontFamily = FontFamily.SansSerif
}

/**
 * One step of depth, as the two blurs it is made of.
 *
 * [key] is the tight contact shadow and [ambient] the wide soft one. They are not a
 * ratio of each other: as a surface rises, its contact shadow stays small and only
 * softens a little while the ambient one spreads a lot. Deriving one from the other
 * is what makes a scale look mechanical.
 */
@Immutable
data class Elevation(val key: Dp, val ambient: Dp)

/**
 * How far off the page a surface sits.
 *
 * Three steps and no more. A staff tool with five elevations is a tool where nobody
 * can tell which two things are at the same level, and the layout's real structure
 * is still the borders — these lift a surface, they do not replace an outline.
 */
object AdminElevation {
    /**
     * On the page, not above it.
     *
     * For the rows of a table, which are all at one depth. Twenty shadows down a
     * list reads as a pile of loose cards rather than as a table.
     */
    val flat = Elevation(key = 0.dp, ambient = 0.dp)

    /** A card, a table, a metric tile. Enough to separate from the page. */
    val card = Elevation(key = 1.dp, ambient = 5.dp)

    /** The rail, and the header bar. Sits over content that scrolls under it. */
    val chrome = Elevation(key = 2.dp, ambient = 14.dp)

    /** A dialog, and the banner. Over everything. */
    val overlay = Elevation(key = 6.dp, ambient = 30.dp)
}

/**
 * Applies a palette, and the Material theme underneath it.
 *
 * [dark] rather than a system-preference read: this is a tool somebody chooses the
 * look of and the choice is remembered, so following an OS setting would override
 * them every launch.
 */
@Composable
fun AdminTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalAdminPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = palette.text,
                    onPrimary = palette.canvas,
                    background = palette.canvas,
                    onBackground = palette.text,
                    surface = palette.card,
                    onSurface = palette.text,
                    surfaceVariant = palette.field,
                    onSurfaceVariant = palette.textFaint,
                    outline = palette.border,
                    error = palette.danger,
                )
            } else {
                lightColorScheme(
                    primary = palette.text,
                    onPrimary = palette.rail,
                    background = palette.canvas,
                    onBackground = palette.text,
                    surface = palette.card,
                    onSurface = palette.text,
                    surfaceVariant = palette.field,
                    onSurfaceVariant = palette.textFaint,
                    outline = palette.border,
                    error = palette.danger,
                )
            },
            typography = Typography(),
            content = content,
        )
    }
}

/**
 * The type scale, in the mockup's own sizes.
 *
 * Named for the job rather than for a Material role, because the design's sizes
 * (10.5, 11.5, 12.5, 13.5) do not map onto `labelSmall`/`bodyMedium` in any way
 * somebody reading a screen would be able to guess.
 */
@Immutable
object AdminType {

    /** Section eyebrows: `RECENT ACTIVITY`, `USER`, `PHONE`. */
    val eyebrow = TextStyle(
        fontFamily = AdminTokens.fontFamily,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp,
    )

    /** A table's column heading — the eyebrow, slightly tighter. */
    val columnHead = eyebrow.copy(letterSpacing = 1.1.sp)

    /** The wordmark. */
    val wordmark = TextStyle(
        fontFamily = AdminTokens.fontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.8.sp,
    )

    /** A page title in the header bar. */
    val title = TextStyle(
        fontFamily = AdminTokens.fontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )

    /** The primary value in a row — a name, a model, a city. */
    val rowPrimary = TextStyle(
        fontFamily = AdminTokens.fontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /**
     * A nav label.
     *
     * Larger and heavier than the mockup's 13.5px/500. That size is fine as
     * browser text with subpixel antialiasing behind it; Compose draws to a canvas
     * with grayscale antialiasing, and at 13.5px in #9CA3AF on near-black the
     * result is genuinely hard to read.
     */
    val navLabel = TextStyle(
        fontFamily = AdminTokens.fontFamily,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Ordinary cell text, and the header subtitle. */
    val body = TextStyle(fontFamily = AdminTokens.fontFamily, fontSize = 12.5.sp)

    /** A status word, a badge, a button label. */
    val strong = TextStyle(
        fontFamily = AdminTokens.fontFamily,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** The second line of a two-line cell. */
    val caption = TextStyle(fontFamily = AdminTokens.fontFamily, fontSize = 11.sp)

    /** The smallest thing on screen — a chart label, a role under a name. */
    val micro = TextStyle(fontFamily = AdminTokens.fontFamily, fontSize = 10.5.sp)

    /** A headline number on a metric card. */
    val metric = TextStyle(
        fontFamily = AdminTokens.fontFamily,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
    )
}
