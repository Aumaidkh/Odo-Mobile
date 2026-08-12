package com.hopcape.odo.feature.billscanner.presentation.scan

import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.navigation.ScanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The choke point behind `FeatureFlags.PAY_VIA_QR_ENABLED`.
 *
 * Worth its own test because everything else about the payment flow is switched off by
 * consequence rather than directly — the frame analyser, the gallery decode and the
 * navigation to `PayAtPump` are all conditioned on the live target, so if this list is wrong
 * the whole feature quietly comes back on. It is also two lines of pure logic, which makes it
 * the cheapest place in the codebase to assert the release decision.
 *
 * Written to hold whichever way the flag is set: the assertions branch on it rather than
 * hard-coding 1.0's answer, so flipping the flag does not leave a red test behind.
 */
class ScanTargetsTest {

    @Test
    fun scanTargets_alwaysOffersTheTwoPaperModes() {
        // Bills and documents are the scanner's reason to exist, flag or no flag.
        assertTrue(ScanTarget.Bill in scanTargets)
        assertTrue(ScanTarget.Document in scanTargets)
    }

    @Test
    fun payQr_isOfferedOnlyWhenTheFlagIsOn() {
        assertEquals(FeatureFlags.PAY_VIA_QR_ENABLED, ScanTarget.PaymentQr in scanTargets)
    }

    @Test
    fun withTheFlagOff_theScannerOffersExactlyTheTwoPaperModes() {
        if (FeatureFlags.PAY_VIA_QR_ENABLED) return

        // Not just "QR is absent" — that nothing else went missing with it. A filter that
        // dropped a mode by accident would leave the scanner unable to read a bill.
        assertEquals(listOf(ScanTarget.Bill, ScanTarget.Document), scanTargets)
    }

    @Test
    fun theOrderMatchesTheEnum_soTheChipsDoNotShuffleWhenTheFlagFlips() {
        // The chips are built straight from this list. Turning payments on should add a chip
        // on the end, not reorder the two an owner already knows the position of.
        assertEquals(ScanTarget.entries.filter { it in scanTargets }, scanTargets)
    }
}
