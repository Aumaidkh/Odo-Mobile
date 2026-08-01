package com.hopcape.odo.core.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the Health Score → status colour thresholds to the PRD §5.4 bands. These
 * boundaries drive the gauge, badges, and copy, so a silent shift would desync them —
 * and they have to keep agreeing with `HealthBand` in `:core:domain`, which is what the
 * label under the dial reads.
 */
class HealthScoreColorTest {

    private val colors = DarkOdoColors

    @Test
    fun belowFifty_isDanger() {
        assertEquals(colors.danger, colors.healthScoreColor(0))
        assertEquals(colors.danger, colors.healthScoreColor(49))
    }

    @Test
    fun fiftyToSixtyNine_isWarning() {
        assertEquals(colors.warning, colors.healthScoreColor(50))
        assertEquals(colors.warning, colors.healthScoreColor(69))
    }

    @Test
    fun seventyAndAbove_isSuccess() {
        // Good (70–84) and Excellent (85–100) share a colour: both are places an owner
        // can be happy to be, and only the label tells them apart.
        assertEquals(colors.success, colors.healthScoreColor(70))
        assertEquals(colors.success, colors.healthScoreColor(84))
        assertEquals(colors.success, colors.healthScoreColor(100))
    }
}
