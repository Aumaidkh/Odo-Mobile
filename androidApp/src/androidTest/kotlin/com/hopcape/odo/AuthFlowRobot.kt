package com.hopcape.odo

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.SignOut
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.core.platform.sms.SmsAppSignature
import com.hopcape.odo.core.platform.sms.SmsCodeReader
import com.hopcape.odo.core.platform.sms.SmsCodeStatus
import com.hopcape.odo.feature.auth.presentation.AuthTestTags
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * The words the sign-in flow puts on screen, mirrored from `:feature:auth`'s `strings.xml`
 * (and the two profile rows the flow is entered and left by).
 *
 * Copied rather than read: Compose Resources keeps a feature's generated `Res` internal to
 * its own module, so `:androidApp` cannot reach it. Asserting on the copy an owner actually
 * reads is the point — a reworded screen *should* make this speak up.
 *
 * Note the typographic apostrophes (’ not '): they come from the resource file, and a plain
 * ASCII quote here would never match.
 */
internal object AuthCopy {
    const val PHONE_TITLE = "What’s your number?"
    const val PHONE_LABEL = "Mobile number"
    const val SEND_CODE = "Send code"
    const val SKIP = "Skip for now"

    const val OTP_TITLE = "Enter the code"
    const val CHANGE = "Change"
    const val RESEND = "Resend code"
    const val RESEND_COUNTDOWN = "Resend code in"
    const val RESEND_EXHAUSTED = "No more codes for now. Check the number, or try again later."
    const val GET_HELP = "Get help"

    const val AUTO_READ_LISTENING = "Watching for your code — no need to leave the app."
    const val AUTO_READ_FILLED = "Code filled automatically."
    const val AUTO_READ_UNAVAILABLE = "Auto-read is off — enter the code below."

    const val VERIFIED_TITLE = "Number verified"

    /* Failures. */
    const val ERROR_SEND_FAILED = "Couldn’t send the code. Check your connection and try again."
    const val ERROR_TOO_MANY = "Too many attempts. Wait a moment before asking again."

    /** The wrong-code line, which counts down as attempts are spent. */
    fun triesLeft(count: Int) = "That code isn’t right. $count tries left."

    /** "Sent to 3210 · " — the last four digits, never the whole number. */
    fun sentTo(lastFour: String) = "Sent to $lastFour · "

    /* The two profile rows the flow is reached from and lands back on. */
    const val PROFILE_SIGN_OUT = "Sign out"
    const val SIGN_OUT_CONFIRM_TITLE = "Sign out of Odo?"
}

internal object AuthFixtures {
    /** Ten digits, so the CTA enables and `PhoneNumber.of` gives it +91. */
    const val TYPED_NUMBER = "9876543210"
    const val LAST_FOUR = "3210"
    const val E164 = "+919876543210"

    /** Nine digits — enough to enable nothing, which is what the CTA gate is for. */
    const val SHORT_NUMBER = "987654321"

    const val CODE = "472915"
    const val WRONG_CODE = "111111"

    const val OWNER = "signed-in-owner"
    const val ACCESS_TOKEN = "test-access-token"
}

private typealias AuthTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ The fakes ------------------------------ */

/**
 * A sign-in server that answers instantly and never leaves the device.
 *
 * The shipped gateway is either the development password account (a real round trip to
 * Supabase that ignores the code, so no wrong-code path exists) or the real OTP gateway
 * (which needs an SMS provider). Neither can drive a test, and both would make the suite
 * depend on a network. This one is the seam the app already has, filled in.
 */
internal class FakeAuthGateway : AuthGateway {

    /** What the next request for a code answers with. */
    var sendResult: Either<DomainError, Unit> = Unit.right()

    /** The one code this "server" accepts; anything else comes back as a wrong code. */
    var acceptedCode: String = AuthFixtures.CODE

    /** Set to refuse every verification with this, whatever was typed. */
    var verifyRefusal: DomainError? = null

    /** How many codes were asked for, so a resend can be proved to have happened. */
    var codesSent: Int = 0
        private set

    /** The number the last code was issued against, in the form the screen parsed it to. */
    var lastPhone: String? = null
        private set

    override suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, Unit> {
        lastPhone = phone.value
        return sendResult.onRight { codesSent++ }
    }

    override suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession> =
        verifyRefusal?.left()
            ?: if (code == acceptedCode) session().right() else DomainError.InvalidOtp.left()

    override suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> = session().right()

    override suspend fun signOut(accessToken: String): Either<DomainError, Unit> = Unit.right()

    private fun session() = AuthSession(
        accessToken = AuthFixtures.ACCESS_TOKEN,
        refreshToken = "test-refresh-token",
        ownerId = OwnerId(AuthFixtures.OWNER),
        expiresAt = Clock.System.now().plus(1.hours),
    )
}

/**
 * Put [gateway] in front of the sign-in screens.
 *
 * Overriding the binding is enough because `OdoSessionManager` resolves its gateway per call
 * (`LateBoundAuthGateway`). Holding one would not work here: the session manager is a
 * `single` the startup path builds before the first test runs.
 */
internal fun installAuthGateway(gateway: AuthGateway) {
    GlobalContext.get().loadModules(
        listOf(module { single<AuthGateway> { gateway } }),
        allowOverride = true,
    )
}

/**
 * Decide what the SMS reader reports.
 *
 * Always replaced, never left to the real one: Play Services' retriever needs a message whose
 * body carries this build's signature hash, so on an emulator it reports nothing at all —
 * and a test that waited for it would wait five minutes.
 *
 * The signature reader goes with it. It reaches Play Services too, and the OTP ViewModel asks
 * it on arrival.
 */
internal fun installSmsReader(status: SmsCodeStatus) {
    GlobalContext.get().loadModules(
        listOf(
            module {
                single<SmsCodeReader> { SmsCodeReader { flowOf(status) } }
                single<SmsAppSignature> { SmsAppSignature { emptyList() } }
            },
        ),
        allowOverride = true,
    )
}

/* ------------------------------ Session state ------------------------------ */

/**
 * End any session this device is holding, and forget the owner's rows with it.
 *
 * Through the app's own `SignOut` port rather than by clearing the store: the in-memory
 * session in `OdoSessionManager` outlives a test, and a token left there would make the next
 * test's profile offer "Sign out" before it had signed in.
 */
internal fun clearSession() = runBlocking { GlobalContext.get().get<SignOut>().invoke() }

/** The stored access token — what says a session survived the screen that created it. */
internal fun storedAccessToken(): String? = runBlocking {
    GlobalContext.get().get<SecureStore>().get(SecureStore.KEY_ACCESS_TOKEN)
}

/* ------------------------------ Navigation ------------------------------ */

/** Open sign-in the way an owner does: the last row of the profile. */
internal fun AuthTestRule.openSignIn() {
    openProfile()
    onNodeWithText(ProfileCopy.SIGN_IN).performScrollTo().performClick()
    awaitText(AuthCopy.PHONE_TITLE)
}

/** Number → code screen, which is the precondition for every code-entry test. */
internal fun AuthTestRule.reachTheCodeScreen(number: String = AuthFixtures.TYPED_NUMBER) {
    openSignIn()
    enterPhoneNumber(number)
    tapSendCode()
    awaitText(AuthCopy.OTP_TITLE)
}

/* ------------------------------ Actions ------------------------------ */

/**
 * Type [number] into the phone field.
 *
 * The tag sits on the component while the node that accepts text is the `BasicTextField`
 * beside the country-code chip, so the editable node is matched within the tagged subtree.
 */
internal fun AuthTestRule.enterPhoneNumber(number: String) {
    typeInto(AuthTestTags.PHONE_FIELD, number)
    waitForIdle()
}

/**
 * Put [code] in the six boxes, replacing whatever is there.
 *
 * Cleared first because a second attempt types over a full field, and the component caps
 * input at six digits — without the clearance the new code would be silently dropped.
 */
internal fun AuthTestRule.enterCode(code: String) {
    val field = onNode(
        hasSetTextAction() and
            (hasTestTag(AuthTestTags.OTP_FIELD) or hasAnyAncestor(hasTestTag(AuthTestTags.OTP_FIELD))),
    )
    field.performTextClearance()
    field.performTextInput(code)
    waitForIdle()
}

internal fun AuthTestRule.tapSendCode() {
    onNodeWithText(AuthCopy.SEND_CODE).performScrollTo().performClick()
    waitForIdle()
}

internal fun AuthTestRule.tapSkipSignIn() {
    onNodeWithText(AuthCopy.SKIP).performScrollTo().performClick()
    waitForIdle()
}

internal fun AuthTestRule.tapChangeNumber() {
    onNodeWithText(AuthCopy.CHANGE).performScrollTo().performClick()
    waitForIdle()
}

internal fun AuthTestRule.tapResend() {
    onNodeWithText(AuthCopy.RESEND).performScrollTo().performClick()
    waitForIdle()
}

/** Confirm sign-out from the profile's last row, which is how a session ends. */
internal fun AuthTestRule.signOutFromProfile() {
    onNodeWithText(AuthCopy.PROFILE_SIGN_OUT).performScrollTo().performClick()
    awaitText(AuthCopy.SIGN_OUT_CONFIRM_TITLE)
    // By tag, not by text: the sheet's confirm reads the same as the row that opened it,
    // and the row is still composed behind it.
    onNodeWithTag(ProfileTestTags.SIGN_OUT_CONFIRM).performClick()
    waitForIdle()
}

/**
 * Wait until Resend is actually offered.
 *
 * The countdown has to be seen *first*. `OtpUiState.resendInSeconds` defaults to zero, so the
 * screen's opening frame offers Resend before the ViewModel has applied the cooldown — a test
 * that waited only for the words would tap in that window and be refused by the throttle.
 */
internal fun AuthTestRule.awaitResendOffered() {
    awaitTextStartingWith(AuthCopy.RESEND_COUNTDOWN)
    waitUntil(AUTH_COOLDOWN_TIMEOUT_MILLIS) { textCount(AuthCopy.RESEND) > 0 }
}

/* ------------------------------ Waiting ------------------------------ */

/**
 * Wait for the verified beat to hand off.
 *
 * The confirmation holds the screen for 1.2 seconds by design, so anything asserted after a
 * correct code has to outlast it.
 */
internal const val AUTH_HANDOFF_TIMEOUT_MILLIS = 10_000L

/** Longer than OtpThrottle's 30-second resend cooldown. */
internal const val AUTH_COOLDOWN_TIMEOUT_MILLIS = 45_000L
