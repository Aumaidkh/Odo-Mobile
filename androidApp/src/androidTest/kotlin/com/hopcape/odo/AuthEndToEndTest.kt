package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.sms.SmsCodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every sign-in flow an owner can reach, driven against the running app: the real Koin graph,
 * the real SQLite database, the real navigation graph and the real ViewModels.
 *
 * **Why the gateway is faked and nothing else is.** The shipped gateway is one of two things,
 * and neither can be driven: with `supabase.phoneAuth = false` it is the development password
 * account, which makes a real round trip and ignores the code entirely, so no wrong-code path
 * exists to test; with it true it sends a real SMS through Firebase and spends real money to
 * do it. [FakeAuthGateway] fills in the seam the app already has, and everything above it —
 * the screens, the ViewModels, the session manager, the secure store, the navigation — is the
 * real thing.
 *
 * What that leaves uncovered is the gateway itself. [PhoneAuthSeamTest] checks the wiring
 * underneath it; the Firebase round trip needs a console-configured test number and is not
 * exercised here.
 *
 * The SMS reader is faked for the same kind of reason: Play Services only delivers a message
 * carrying this build's signature hash, so on an emulator the real reader reports nothing for
 * five minutes.
 *
 * **Where the flow is entered from.** Profile's last row, not first-run onboarding. Both lead
 * to the same `Auth.Phone` key, and the onboarding suite already covers its own hand-off;
 * re-walking car setup here would only make these slower and give them a second reason to
 * fail.
 *
 * **Before running:** clear the app's data or uninstall. The local database still has no
 * migrations, so an install carrying an older one is missing tables these tests read.
 */
@RunWith(AndroidJUnit4::class)
class AuthEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var gateway: FakeAuthGateway

    /**
     * Start every test signed out, on a set-up device, with a server that answers.
     *
     * The activity is recreated last because the rule launches it before this runs, so it
     * may already have read a previous test's data — and where the app opens is decided
     * once per launch.
     */
    @Before
    fun startSignedOutOnASetUpDevice() {
        gateway = FakeAuthGateway()
        installAuthGateway(gateway)
        installSmsReader(SmsCodeStatus.Listening)
        clearSession()
        resetProfile()
        seedOnboardedOwner()
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ Getting in and out ------------------------------ */

    @Test
    fun theProfilesSignInRow_opensTheNumberScreen() {
        rule.openSignIn()

        rule.onNodeWithText(AuthCopy.PHONE_TITLE).assertIsDisplayed()
        rule.onNodeWithText(AuthCopy.PHONE_LABEL).assertIsDisplayed()
    }

    /* ------------------------------ Saying what is happening ------------------------------ */

    /**
     * Sending is not instant — Firebase runs app verification before the SMS is even asked
     * for, which on a cold Play Services can take seconds. A button that only greyed out read
     * as "not now" rather than "working", which is what this replaces.
     */
    @Test
    fun whileTheCodeIsBeingSent_theButtonSaysSo() {
        rule.openSignIn()
        rule.enterPhoneNumber(AuthFixtures.TYPED_NUMBER)
        gateway.holdRequests()

        rule.tapSendCode()

        rule.awaitText(AuthCopy.SENDING_CODE)
        // The idle label is gone, not merely covered — otherwise the screen would be offering
        // an action it is already performing.
        rule.onNodeWithText(AuthCopy.SEND_CODE).assertDoesNotExist()

        gateway.release()
        rule.awaitText(AuthCopy.OTP_TITLE)
    }

    /**
     * The code screen has no Verify button — the last digit submits by itself, so a code that
     * arrives by SMS signs in with nothing tapped. That leaves nowhere to put a spinner, so
     * the wait is reported where the auto-read card and the countdown were.
     */
    @Test
    fun whileTheCodeIsBeingVerified_theScreenSaysSo() {
        rule.reachTheCodeScreen()
        gateway.holdRequests()

        rule.enterCode(AuthFixtures.CODE)

        rule.awaitText(AuthCopy.VERIFYING_CODE)
        // The card claims to be watching for a code that has already been typed and sent.
        rule.onNodeWithText(AuthCopy.AUTO_READ_LISTENING).assertDoesNotExist()

        gateway.release()
        rule.awaitText(AuthCopy.VERIFIED_TITLE)
    }

    @Test
    fun skippingFromTheNumberScreen_returnsWhereItCameFrom_stillSignedOut() {
        rule.openSignIn()

        rule.tapSkipSignIn()

        // Signing in is a prompt, never a gate: declining has to land somewhere useful.
        rule.awaitText(ProfileCopy.TITLE)
        assertNull(storedAccessToken())
    }

    @Test
    fun backFromTheNumberScreen_declinesRatherThanDeadEnding() {
        rule.openSignIn()

        rule.onNodeWithLabel(BACK).performClick()

        // Onboarding clears the stack behind sign-in, so a plain pop would do nothing at
        // all — back has to mean the same as "Skip for now".
        rule.awaitText(ProfileCopy.TITLE)
    }

    /* ------------------------------ The number ------------------------------ */

    @Test
    fun sendCode_staysDisabledUntilTheNumberIsLongEnough() {
        rule.openSignIn()

        rule.enterPhoneNumber(AuthFixtures.SHORT_NUMBER)

        rule.onNodeWithText(AuthCopy.SEND_CODE).performScrollTo().assertIsNotEnabled()
        assertEquals(0, gateway.codesSent)
    }

    @Test
    fun aValidNumber_asksForACodeAgainstTheParsedNumber() {
        rule.openSignIn()

        rule.enterPhoneNumber(AuthFixtures.TYPED_NUMBER)
        rule.tapSendCode()

        rule.awaitText(AuthCopy.OTP_TITLE)
        // The parsed E.164 number travels, not the ten digits that were typed — the code
        // has to be verified against exactly what it was issued for.
        assertEquals(AuthFixtures.E164, gateway.lastPhone)
        assertEquals(1, gateway.codesSent)
    }

    @Test
    fun theCodeScreen_namesOnlyTheLastFourDigits() {
        rule.reachTheCodeScreen()

        // A full number on screen is a full number in a screenshot.
        rule.awaitTextStartingWith(AuthCopy.sentTo(AuthFixtures.LAST_FOUR))
        assertEquals(0, rule.textCount(AuthFixtures.TYPED_NUMBER))
    }

    @Test
    fun aFailedSend_keepsTheOwnerOnTheNumberScreenAndSaysWhy() {
        gateway.sendResult = DomainError.OtpRequestFailed.left()
        rule.openSignIn()

        rule.enterPhoneNumber(AuthFixtures.TYPED_NUMBER)
        rule.tapSendCode()

        // Still here, because no code went out.
        rule.onNodeWithText(AuthCopy.PHONE_TITLE).assertIsDisplayed()
        // And told so: a button that does nothing with no explanation is the worst outcome.
        rule.awaitText(AuthCopy.ERROR_SEND_FAILED)
    }

    /* ------------------------------ The code ------------------------------ */

    @Test
    fun theCodeScreen_saysItIsWatchingForTheSms() {
        rule.reachTheCodeScreen()

        rule.awaitText(AuthCopy.AUTO_READ_LISTENING)
    }

    @Test
    fun whenAutoReadCannotWork_theCardStopsClaimingToListen() {
        installSmsReader(SmsCodeStatus.Unsupported)

        rule.reachTheCodeScreen()

        rule.awaitText(AuthCopy.AUTO_READ_UNAVAILABLE)
    }

    @Test
    fun aCodeArrivingBySms_signsInWithoutAnythingBeingTyped() {
        installSmsReader(SmsCodeStatus.Received(AuthFixtures.CODE))

        rule.reachTheCodeScreen()

        // The card's "filled" state is deliberately not asserted: the code verifies the
        // instant it lands, so that frame races the hand-off. What matters is that nothing
        // was typed and the owner is signed in.
        rule.awaitText(AuthCopy.VERIFIED_TITLE, timeoutMillis = AUTH_HANDOFF_TIMEOUT_MILLIS)
        assertEquals(AuthFixtures.ACCESS_TOKEN, storedAccessToken())
    }

    @Test
    fun theRightCode_confirmsAndKeepsTheSession() {
        rule.reachTheCodeScreen()

        rule.enterCode(AuthFixtures.CODE)

        rule.awaitText(AuthCopy.VERIFIED_TITLE, timeoutMillis = AUTH_HANDOFF_TIMEOUT_MILLIS)
        // The confirmation hands off on its own, back to wherever sign-in was opened from.
        rule.awaitText(ProfileCopy.TITLE, timeoutMillis = AUTH_HANDOFF_TIMEOUT_MILLIS)
        // Stored, not just held: a session lost on restart is not a session.
        assertEquals(AuthFixtures.ACCESS_TOKEN, storedAccessToken())
    }

    @Test
    fun afterSigningIn_theProfileOffersSignOutInsteadOfSignIn() {
        rule.reachTheCodeScreen()

        rule.enterCode(AuthFixtures.CODE)
        rule.awaitText(ProfileCopy.TITLE, timeoutMillis = AUTH_HANDOFF_TIMEOUT_MILLIS)

        // The row an owner is looking at the moment they finish signing in.
        rule.awaitText(AuthCopy.PROFILE_SIGN_OUT)
    }

    @Test
    fun aWrongCode_saysSoAndCountsTheTriesLeft() {
        rule.reachTheCodeScreen()

        rule.enterCode(AuthFixtures.WRONG_CODE)

        rule.awaitText(AuthCopy.triesLeft(2))
        assertNull(storedAccessToken())
    }

    @Test
    fun aWrongCode_canBeCorrected() {
        rule.reachTheCodeScreen()
        rule.enterCode(AuthFixtures.WRONG_CODE)
        rule.awaitText(AuthCopy.triesLeft(2))

        rule.enterCode(AuthFixtures.CODE)

        // The screen promises two more tries; the field has to let the owner spend one.
        rule.awaitText(AuthCopy.VERIFIED_TITLE, timeoutMillis = AUTH_HANDOFF_TIMEOUT_MILLIS)
    }

    @Test
    fun threeWrongCodes_sendTheOwnerBackToTheNumber() {
        rule.reachTheCodeScreen()

        rule.enterCode(AuthFixtures.WRONG_CODE)
        rule.awaitText(AuthCopy.triesLeft(2))
        rule.enterCode(AuthFixtures.WRONG_CODE)
        rule.awaitText(AuthCopy.triesLeft(1))
        rule.enterCode(AuthFixtures.WRONG_CODE)

        // After three misses the likeliest explanation is the wrong number, not the wrong
        // code, so the screen stops offering a fourth attempt.
        rule.awaitText(AuthCopy.PHONE_TITLE)
    }

    @Test
    fun anExpiredCode_saysItExpiredAndDoesNotCostAnAttempt() {
        gateway.verifyRefusal = DomainError.OtpExpired
        rule.reachTheCodeScreen()

        rule.enterCode(AuthFixtures.CODE)

        // The owner did nothing wrong, so the copy must not blame the code they typed, and
        // the attempt must not be spent.
        rule.awaitText(ERROR_CODE_EXPIRED)
        assertEquals(0, rule.textCount(AuthCopy.triesLeft(2)))
    }

    /* ------------------------------ Resending ------------------------------ */

    @Test
    fun resendIsNotOfferedWhileTheCountdownRuns() {
        rule.reachTheCodeScreen()

        rule.awaitTextStartingWith(AuthCopy.RESEND_COUNTDOWN)
        // The cooldown exists because a second code invalidates the first, so a live Resend
        // during it would make things worse rather than better.
        assertEquals(0, rule.textCount(AuthCopy.RESEND))
    }

    @Test
    fun onceTheCountdownLapses_resendSendsAnotherCode() {
        rule.reachTheCodeScreen()
        assertEquals(1, gateway.codesSent)

        rule.awaitResendOffered()
        rule.tapResend()

        rule.waitUntil(AUTH_HANDOFF_TIMEOUT_MILLIS) { gateway.codesSent == 2 }
    }

    /* ------------------------------ Leaving the code screen ------------------------------ */

    @Test
    fun change_goesBackToTheNumberToFixIt() {
        rule.reachTheCodeScreen()

        rule.tapChangeNumber()

        rule.awaitText(AuthCopy.PHONE_TITLE)
    }

    @Test
    fun skippingFromTheCodeScreen_leavesAuthSignedOut() {
        rule.reachTheCodeScreen()

        rule.tapSkipSignIn()

        rule.awaitText(ProfileCopy.TITLE)
        assertNull(storedAccessToken())
    }

    /* ------------------------------ The round trip ------------------------------ */

    @Test
    fun signingOutAfterSigningIn_endsTheSessionAndReturnsToFirstRun() {
        rule.reachTheCodeScreen()
        rule.enterCode(AuthFixtures.CODE)
        rule.awaitText(ProfileCopy.TITLE, timeoutMillis = AUTH_HANDOFF_TIMEOUT_MILLIS)
        assertNotNull(storedAccessToken())
        // Away and back, so the row is read by a screen that opened after the session did.
        rule.leaveProfile()
        rule.openProfile()

        rule.signOutFromProfile()

        // Nothing of the owner's is left, so the app is back to first run — and the token
        // has to be gone with it, or the next launch is signed in to nothing.
        rule.awaitText(ProfileCopy.WELCOME_HEADLINE, timeoutMillis = AUTH_HANDOFF_TIMEOUT_MILLIS)
        assertNull(storedAccessToken())
    }

    private companion object {
        const val BACK = "Back"

        /** `au_error_code_expired`, which the code screen has to prefer over the wrong-code line. */
        const val ERROR_CODE_EXPIRED = "That code has expired. Ask for a new one."
    }
}
