package com.hopcape.odo.web.blog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The blog's two skins.
 *
 * They are not a light mode and a dark mode of one design — they are two designs,
 * and the split is deliberate. The public side is black because a reader arrives
 * from a search result and the page has to look like the brand; the CMS is white
 * because writing long form on black is tiring, and it is a tool with one user
 * rather than a brand surface.
 *
 * Neither follows the system preference. A reader who has their machine in light
 * mode still gets the black blog, because that is the design, and an author in
 * dark mode still gets the white editor, for the same reason.
 *
 * Values are lifted from the design files rather than re-derived, so the drawn
 * page and this file cannot disagree.
 */
@Immutable
data class BlogColors(
    /** The page. */
    val background: Color,
    /** A card sitting on the page. */
    val surface: Color,
    /** A card that has to sit on top of another one. */
    val surfaceRaised: Color,
    /** Hairlines and card edges. */
    val border: Color,
    /** A border that has to be seen — a focused field, a selected chip. */
    val borderStrong: Color,
    /** Headings and body copy. */
    val text: Color,
    /** Copy that should recede: deks, dates, table cells. */
    val dim: Color,
    /** Eyebrows, column headers, placeholders. The quietest readable thing. */
    val muted: Color,
    /** A filled button's background, and its text. */
    val actionBackground: Color,
    val actionText: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    /** The blue in the Google preview. Only ever used there. */
    val link: Color,
)

/** Public — black, the reader-facing side. */
val PublicColors = BlogColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0C0C0C),
    surfaceRaised = Color(0xFF141414),
    border = Color(0xFF1F1F1F),
    borderStrong = Color(0xFF262626),
    text = Color(0xFFFFFFFF),
    dim = Color(0xFF9CA3AF),
    muted = Color(0xFF6B7280),
    actionBackground = Color(0xFFFFFFFF),
    actionText = Color(0xFF000000),
    success = Color(0xFF15803D),
    danger = Color(0xFFB91C1C),
    warning = Color(0xFFD97706),
    link = Color(0xFF1A0DAB),
)

/** Admin — white, the tool. */
val AdminColors = BlogColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF4F4F4),
    border = Color(0xFFD8D8D8),
    borderStrong = Color(0xFF262626),
    text = Color(0xFF000000),
    dim = Color(0xFF374151),
    muted = Color(0xFF6B7280),
    actionBackground = Color(0xFF000000),
    actionText = Color(0xFFFFFFFF),
    success = Color(0xFF15803D),
    danger = Color(0xFFB91C1C),
    warning = Color(0xFFD97706),
    link = Color(0xFF1A0DAB),
)

val LocalBlogColors: ProvidableCompositionLocal<BlogColors> = staticCompositionLocalOf { PublicColors }

/**
 * True when the viewport is phone width.
 *
 * The design has a desktop frame and a mobile frame for the same page, and the
 * differences are structural, not a reflow: the article's contents rail becomes a
 * collapsible strip, the index grid becomes a column. Composables read this and
 * branch, because there is no media query to lean on.
 */
val LocalCompact: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** Below this, the design switches to its mobile frames. */
const val COMPACT_WIDTH_DP: Int = 860

/**
 * Wraps a screen in one of the two skins.
 *
 * Material 3 sits underneath because Compose's own components want a scheme, but
 * nothing here draws a Material surface — [BlogColors] is what the screens read,
 * and the scheme exists so a text field's cursor and selection are the right
 * colour.
 */
@Composable
fun BlogTheme(
    colors: BlogColors,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = colors == PublicColors
    CompositionLocalProvider(
        LocalBlogColors provides colors,
        LocalCompact provides compact,
    ) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = colors.text,
                    background = colors.background,
                    onBackground = colors.text,
                    surface = colors.surface,
                    onSurface = colors.text,
                )
            } else {
                lightColorScheme(
                    primary = colors.text,
                    background = colors.background,
                    onBackground = colors.text,
                    surface = colors.surface,
                    onSurface = colors.text,
                )
            },
            typography = BlogTypography,
            content = content,
        )
    }
}

/** Shorthand so screens read `BlogTheme.colors` instead of the local's name. */
object BlogThemeTokens {
    val colors: BlogColors
        @Composable get() = LocalBlogColors.current

    val compact: Boolean
        @Composable get() = LocalCompact.current
}
