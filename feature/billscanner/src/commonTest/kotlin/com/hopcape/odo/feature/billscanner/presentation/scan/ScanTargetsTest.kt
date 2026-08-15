package com.hopcape.odo.feature.billscanner.presentation.scan

import com.hopcape.odo.core.navigation.ScanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The list every scan mode comes from.
 *
 * Worth its own test because everything else follows the live target rather than deciding for
 * itself — the frame analyser, the chips and where a capture goes next are all conditioned on
 * it. A mode missing from this list is a capture channel silently unreachable, and a mode
 * wrongly in it is the opposite.
 */
class ScanTargetsTest {

    @Test
    fun scanTargets_alwaysOffersTheTwoPaperModes() {
        // Bills and documents are the scanner's reason to exist.
        assertTrue(ScanTarget.Bill in scanTargets)
        assertTrue(ScanTarget.Document in scanTargets)
    }

    @Test
    fun pumpDisplay_isOffered() {
        // The one mode that works in every market: a pump shows what it dispensed whether the
        // owner paid by card, by phone or in cash.
        assertTrue(ScanTarget.PumpDisplay in scanTargets)
    }

    @Test
    fun theScannerOffersEveryModeAndNothingElse() {
        // Not just "the new mode is present" — that nothing went missing alongside it. A list
        // that dropped a mode by accident would leave the scanner unable to read a bill.
        assertEquals(
            listOf(
                ScanTarget.Bill,
                ScanTarget.Document,
                ScanTarget.PumpDisplay,
            ),
            scanTargets,
        )
    }

    @Test
    fun theOrderMatchesTheEnum_soTheChipsDoNotShuffle() {
        // The chips are built straight from this list, and an owner learns where each one is.
        assertEquals(ScanTarget.entries.toList(), scanTargets)
    }
}
