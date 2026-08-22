package com.hopcape.odo.web.blog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale, taken off the design's own frames.
 *
 * Two things are worth knowing before changing a number here.
 *
 * The design is a 1440-wide artboard, so its pixel values are what a desktop
 * browser renders at that width, and they map to sp one for one.
 *
 * And the family is [FontFamily.Default], not Inter. Compose draws to a canvas,
 * so it cannot use the webfont the page loads — it needs the font bytes, and
 * bundling Inter would add roughly a megabyte to a payload that is already large.
 * The rest of odoapp.in sets `Gotham, Inter, system-sans`, so this is the third
 * fallback rather than a different design. Swapping it later is this one line
 * plus the .ttf files.
 */
private val Sans = FontFamily.Default

internal val BlogTypography = Typography().run {
    copy(
        // An article title, desktop.
        displayLarge = TextStyle(
            fontFamily = Sans,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 1.08.em,
            letterSpacing = (-0.02).em,
        ),
        // The index's own heading, and an article title on a phone.
        displayMedium = TextStyle(
            fontFamily = Sans,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 1.12.em,
            letterSpacing = (-0.02).em,
        ),
        // The lead story's title, a section heading inside an article.
        headlineLarge = TextStyle(
            fontFamily = Sans,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 1.2.em,
            letterSpacing = (-0.015).em,
        ),
        headlineMedium = TextStyle(
            fontFamily = Sans,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 1.25.em,
            letterSpacing = (-0.01).em,
        ),
        // A card title in the grid.
        headlineSmall = TextStyle(
            fontFamily = Sans,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 1.3.em,
            letterSpacing = (-0.01).em,
        ),
        // Article body. The 680px measure was drawn at this size.
        titleLarge = TextStyle(
            fontFamily = Sans,
            fontSize = 17.sp,
            lineHeight = 1.7.em,
        ),
        titleMedium = TextStyle(
            fontFamily = Sans,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 1.4.em,
        ),
        bodyLarge = TextStyle(
            fontFamily = Sans,
            fontSize = 15.sp,
            lineHeight = 1.55.em,
        ),
        // Card deks, table cells — most of the small copy on the site.
        bodyMedium = TextStyle(
            fontFamily = Sans,
            fontSize = 13.sp,
            lineHeight = 1.5.em,
        ),
        bodySmall = TextStyle(
            fontFamily = Sans,
            fontSize = 12.sp,
            lineHeight = 1.45.em,
        ),
        // Nav links, buttons.
        labelLarge = TextStyle(
            fontFamily = Sans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.005.em,
        ),
        labelMedium = TextStyle(
            fontFamily = Sans,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
        // Eyebrows and column headers. The wide tracking is the design's, and it
        // is what makes ten-pixel uppercase readable at all.
        labelSmall = TextStyle(
            fontFamily = Sans,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.18.em,
        ),
    )
}
