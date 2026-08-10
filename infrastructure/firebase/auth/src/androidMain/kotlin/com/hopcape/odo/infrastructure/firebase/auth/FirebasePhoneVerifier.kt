package com.hopcape.odo.infrastructure.firebase.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.app.CurrentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * Phone verification through Firebase.
 *
 * Firebase is used for exactly one thing: proving the number. The session that comes out of
 * sign-in is still a Supabase session, because every `owner_id` in this app is a `uuid`
 * referencing `auth.users(id)` and a Firebase UID is not one. What this hands back is a
 * Firebase ID token, and `:infrastructure:supabase` trades it for the real session.
 *
 * Android-only, and not because of the usual KMP reasons: `verifyPhoneNumber` has no
 * Activity-free overload. Play Integrity attaches to a window, and so does the reCAPTCHA
 * fallback it drops to when Play Integrity is unavailable — a device with no Play Services,
 * or a build whose SHA-256 is not registered in the Firebase console.
 */
internal class FirebasePhoneVerifier(
    private val auth: FirebaseAuth,
    private val activities: CurrentActivity,
    private val onDiagnostic: (String) -> Unit,
) : PhoneVerifier {

    /**
     * The handle Firebase pairs with the typed code. Written from the SDK's callback thread
     * and read from the verifying coroutine, hence `@Volatile`.
     */
    @Volatile
    private var verificationId: String? = null

    /**
     * The credential from auto-retrieval, when the SDK reads the SMS itself.
     *
     * Kept as a fallback rather than acted on: acting on it would mean signing in while the
     * owner is still looking at the code screen, and telling them so needs a channel
     * `AuthGateway` does not have. Using it only if [submitCode] arrives with no
     * [verificationId] costs nothing and closes the one gap where there would otherwise be
     * no way to finish.
     */
    @Volatile
    private var autoRetrieved: PhoneAuthCredential? = null

    override suspend fun startVerification(phone: PhoneNumber): Either<DomainError, Unit> {
        val activity = activities.get()
        if (activity == null) {
            onDiagnostic("No Activity is up, so Firebase cannot run Play Integrity or reCAPTCHA. No code sent.")
            return DomainError.OtpRequestFailed.left()
        }
        verificationId = null
        autoRetrieved = null

        return runCatchingCancellableSuspend {
            suspendCancellableCoroutine { continuation ->
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                        verificationId = id
                        if (continuation.isActive) continuation.resume(Unit.right())
                    }

                    /**
                     * Reached only if the SDK reads the SMS on this device. [requireSmsValidation]
                     * below rules out the other case — instant verification, where no message is
                     * sent at all and `onCodeSent` never fires, which would leave this coroutine
                     * waiting for a callback that is not coming.
                     */
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        autoRetrieved = credential
                        if (continuation.isActive) continuation.resume(Unit.right())
                    }

                    override fun onVerificationFailed(error: FirebaseException) {
                        onDiagnostic("Firebase refused to send a code: ${error.diagnostic()}")
                        if (continuation.isActive) continuation.resume(error.toSendFailure().left())
                    }

                    /**
                     * Not a failure. The window for reading the SMS automatically has closed;
                     * the owner types the code, which is the path this class expects anyway.
                     */
                    override fun onCodeAutoRetrievalTimeOut(id: String) {
                        verificationId = id
                    }
                }

                PhoneAuthProvider.verifyPhoneNumber(
                    PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phone.value)
                        .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(callbacks)
                        // Forces a real SMS and a typed code. Without it Firebase may verify
                        // the number silently and never call onCodeSent, which both hangs the
                        // callback above and signs someone in without them typing anything.
                        .requireSmsValidation(true)
                        .build()
                )
            }
        }.getOrElse { error ->
            onDiagnostic("Firebase threw while starting verification: ${error.diagnostic()}")
            DomainError.OtpRequestFailed.left()
        }
    }

    override suspend fun submitCode(code: String): Either<DomainError, VerifiedPhoneToken> {
        val credential = verificationId?.let { PhoneAuthProvider.getCredential(it, code) }
            ?: autoRetrieved
            // Nothing in flight — the process died between the two screens, or nobody asked
            // for a code. Either way the answer is resend, not retype.
            ?: return DomainError.OtpExpired.left()

        return runCatchingCancellableSuspend {
            val user = auth.signInWithCredential(credential).await().user
                ?: error("Firebase signed in with no user attached")
            // false: the token was minted by the sign-in that just returned, so forcing a
            // refresh would be a second round trip for the same token.
            user.getIdToken(false).await().token
                ?: error("Firebase returned a signed-in user with no ID token")
        }.fold(
            onSuccess = { VerifiedPhoneToken(it).right() },
            onFailure = { error ->
                onDiagnostic("Firebase refused the code: ${error.diagnostic()}")
                error.toVerifyFailure().left()
            },
        )
    }

    /**
     * Signing out of Firebase is not optional here. It keeps its own signed-in user
     * independently of Odo's session, and leaving one behind means the next person to open
     * the app on this device is already verified as the last one.
     */
    override suspend fun forget() {
        verificationId = null
        autoRetrieved = null
        runCatchingCancellableSuspend { auth.signOut() }
            .onFailure { onDiagnostic("Firebase sign-out failed: ${it.diagnostic()}") }
    }

    private companion object {

        /**
         * How long the SDK may keep trying to read the SMS itself before falling back to a
         * typed code. Firebase caps this at 120s; 60 matches how long a code stays useful
         * and is well past the point where an owner has given up waiting and typed it.
         */
        const val AUTO_RETRIEVAL_TIMEOUT_SECONDS = 60L
    }
}

/**
 * How long to tell the owner to wait after Firebase rate-limits them.
 *
 * Firebase does not say how long its limit lasts, so this is the app's own answer rather than
 * the server's — the same 30 seconds `OtpThrottle` already makes the owner wait between
 * codes, so the countdown does not change shape when the limit turns out to be Firebase's
 * rather than ours.
 */
private const val RATE_LIMIT_COOLDOWN_SECONDS = 30L

/**
 * What went wrong asking for a code.
 *
 * `ERROR_INVALID_PHONE_NUMBER` is the one worth separating: the number itself is wrong, so
 * resending will fail identically, and the owner has to go back and fix it.
 */
internal fun Throwable.toSendFailure(): DomainError = when {
    this is FirebaseTooManyRequestsException ->
        DomainError.TooManyOtpRequests(retryAfterSeconds = RATE_LIMIT_COOLDOWN_SECONDS)

    this is FirebaseAuthInvalidCredentialsException -> DomainError.InvalidPhoneNumber

    else -> DomainError.OtpRequestFailed
}

/**
 * What went wrong submitting a code.
 *
 * Wrong and expired are kept apart because the answer differs — retype, or resend — which is
 * the distinction [DomainError.InvalidOtp] and [DomainError.OtpExpired] exist to carry.
 */
internal fun Throwable.toVerifyFailure(): DomainError = when {
    this is FirebaseTooManyRequestsException ->
        DomainError.TooManyOtpRequests(retryAfterSeconds = RATE_LIMIT_COOLDOWN_SECONDS)

    (this as? FirebaseAuthException)?.errorCode == "ERROR_SESSION_EXPIRED" -> DomainError.OtpExpired

    this is FirebaseAuthInvalidCredentialsException -> DomainError.InvalidOtp

    else -> DomainError.OtpRequestFailed
}

/**
 * Firebase's own error code plus the exception type, and never the message.
 *
 * The message can carry the number that was being verified, and a log line is a bug-report
 * line (TDD §12).
 */
private fun Throwable.diagnostic(): String {
    val code = (this as? FirebaseAuthException)?.errorCode
    return if (code != null) "${this::class.simpleName}/$code" else this::class.simpleName.orEmpty()
}
