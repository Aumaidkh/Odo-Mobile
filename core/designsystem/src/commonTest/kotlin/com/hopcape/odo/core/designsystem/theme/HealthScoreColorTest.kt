package com.hopcape.odo.core.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the Health Score → status colour thresholds to the spec ("danger =
 * score < 50"). These boundaries drive the gauge, badges, and copy, so a silent
 * shift would desync them.
 */
class HealthScoreColorTest {

    private val colors = DarkOdoColors

    @Test
    fun belowFifty_isDanger() {
        assertEquals(colors.danger, colors.healthScoreColor(0))
        assertEquals(colors.danger, colors.healthScoreColor(49))
    }

    @Test
    fun fiftyToSeventyNine_isWarning() {
        assertEquals(colors.warning, colors.healthScoreColor(50))
        assertEquals(colors.warning, colors.healthScoreColor(79))
    }

    @Test
    fun eightyAndAbove_isSuccess() {
        assertEquals(colors.success, colors.healthScoreColor(80))
        assertEquals(colors.success, colors.healthScoreColor(100))
    }
}
