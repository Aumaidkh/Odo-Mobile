package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.platform.sms.SmsCodeStatus
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The code screen while the request for the code is still out (#438).
 *
 * It is the frame the bug lived in: the countdown read "01:00" — before the fix, "00:00" — for
 * the whole Firebase round trip, because the wait was anchored to the provider's answer rather
 * than to the moment the code was asked for.
 *
 * **How the moment is held.** [FakeAuthGateway.holdRequests] suspends the request until the
 * test lets it go, which is the only way to look at this screen: the fake otherwise answers on
 * the frame it is called, and a real gateway would send a billed SMS.
 *
 * Captured with [Screenshots.capture] rather than `captureScreen`. The auto-read card's halo
 * and dots are infinite animations, so `waitForIdle` has nothing to return for — [waitUntil]
 * is what drives the clock far enough for the ticked value to be drawn.
 */
@RunWith(AndroidJUnit4::class)
class OtpCountdownScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var gateway: FakeAuthGateway

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { startSignedOutWithTheRequestHeld() })
        .around(rule)

    private fun startSignedOutWithTheRequestHeld() {
        gateway = FakeAuthGateway().apply { holdRequests() }
        installAuthGateway(gateway)
        installSmsReader(SmsCodeStatus.Listening)
        clearSession()
        resetProfile()
        seedOnboardedOwner()
    }

    /** Release the held request, so neither it nor the session outlives this class. */
    @After
    fun endSignedOut() {
        gateway.release()
        clearSession()
    }

    @Test
    fun theCountdownWhileTheCodeIsStillBeingAskedFor() {
        rule.requestTheCode()
        rule.awaitText(AuthCopy.OTP_TITLE)

        // "00:" is the whole assertion: the countdown has dropped under a minute, so it is
        // running while the request is still out rather than waiting for it to come back.
        rule.waitUntil(TICK_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText(COUNTING_DOWN, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        Screenshots.capture("otp-countdown-while-sending")
    }

    private companion object {
        const val COUNTING_DOWN = "${AuthCopy.RESEND_COUNTDOWN} 00:"
        const val TICK_TIMEOUT_MILLIS = 10_000L
    }
}
