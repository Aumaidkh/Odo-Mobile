package com.hopcape.odo.core.designsystem.theme

import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shimmer's two colours, in the themes that actually ship.
 *
 * A skeleton's sweep has one job: to say the screen is alive. A highlight darker than the block
 * it crosses reads as a shadow passing over instead, which is what the dark theme did.
 */
class ShimmerColorTest {

    /** What `LocalOdoColors` resolves to — the Tesla sets, not [DarkOdoColors]. */
    private val schemes = listOf(
        "dark" to TeslaDarkOdoColors,
        "light" to TeslaLightOdoColors,
    )

    @Test
    fun theHighlightIsLighterThanTheBlockItSweepsAcross() {
        schemes.forEach { (name, colors) ->
            val base = colors.shimmerBase.luminance()
            val highlight = colors.shimmerHighlight.luminance()
            assertTrue(
                highlight > base,
                "$name: the highlight ($highlight) has to be lighter than the block ($base)",
            )
        }
    }

    /**
     * Enough of a step to be seen.
     *
     * Measured as a contrast ratio rather than a luminance gap: luminance is compressed near
     * black, so a fixed gap that is modest on the light theme is unreachable on the dark one
     * and would force a highlight bright enough to flash.
     */
    @Test
    fun theSweepIsWideEnoughToBeVisible() {
        schemes.forEach { (name, colors) ->
            val ratio = contrastRatio(colors.shimmerHighlight.luminance(), colors.shimmerBase.luminance())
            assertTrue(
                ratio >= MIN_SWEEP_CONTRAST,
                "$name: the sweep is a contrast ratio of $ratio, which reads as a flat block",
            )
        }
    }

    /** Signed on purpose — below 1 means the "highlight" is darkening the block. */
    private fun contrastRatio(highlight: Float, base: Float) = (highlight + OFFSET) / (base + OFFSET)

    /** The block stands in for a card, so it must not be mistaken for the page behind it. */
    @Test
    fun theBlockIsDistinctFromTheBackground() {
        schemes.forEach { (name, colors) ->
            val block = colors.shimmerBase.luminance()
            val background = colors.bg.luminance()
            assertTrue(
                block != background,
                "$name: the block is the same colour as the page behind it",
            )
        }
    }

    private companion object {
        /** WCAG's offset, which is what keeps the ratio meaningful at the black end. */
        const val OFFSET = 0.05f

        /**
         * Modest by contrast-ratio standards, because this is a moving highlight rather than
         * text: it only has to be seen, not read.
         */
        const val MIN_SWEEP_CONTRAST = 1.15f
    }
}
