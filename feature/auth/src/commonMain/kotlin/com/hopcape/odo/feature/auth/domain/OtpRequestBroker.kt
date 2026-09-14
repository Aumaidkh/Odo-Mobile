package com.hopcape.odo.feature.auth.domain

import com.hopcape.odo.core.domain.auth.OtpRequestOutcome
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one outstanding request for a code, started on one screen and read on the next.
 *
 * The number screen asks for the code and moves on at once; the code screen shows what
 * happened. Neither owns the work, because the screen that starts it is gone before it
 * finishes — so it lives here, on a scope that outlives both (#409).
 *
 * **The code screen never starts one.** That is the whole reason this is not simply a call in
 * the code screen's `init`: the back stack is serialized, so a process death while the owner
 * is in their SMS app restores that screen and rebuilds its ViewModel — and an `init` that
 * requests would send a second billed SMS with nobody having asked for one, on a fresh
 * throttle that would let it happen again.
 *
 * Which is why [observe] answers [OtpRequest.Sent] when it holds nothing for that number: a
 * restored screen is one whose code went out before the process died. That is the assumption
 * the code screen has always made, and it is safe in the only direction that matters — it
 * offers a resend rather than sending one.
 */
internal class OtpRequestBroker(
    private val sessions: OdoSessionManager,
    /** Process-lifetime: the request has to survive the screen that asked for it. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val outstanding = MutableStateFlow<Pair<PhoneNumber, OtpRequest>?>(null)

    /**
     * Ask for a code, and return without waiting.
     *
     * A second call for the same number while one is in flight is ignored, which is what
     * stops a double tap on the number screen buying two SMS.
     */
    fun request(phone: PhoneNumber) {
        val held = outstanding.value
        if (held?.first == phone && held.second == OtpRequest.InFlight) return

        outstanding.value = phone to OtpRequest.InFlight
        scope.launch {
            val result = sessions.requestOtp(phone).fold(
                ifLeft = { OtpRequest.Failed(it) },
                ifRight = { outcome ->
                    when (outcome) {
                        OtpRequestOutcome.CodeSent -> OtpRequest.Sent
                        is OtpRequestOutcome.AlreadyVerified -> OtpRequest.Verified
                    }
                },
            )
            outstanding.value = phone to result
        }
    }

    /** What became of the request for [phone]. See the class doc for why absent reads as sent. */
    fun observe(phone: PhoneNumber): Flow<OtpRequest> =
        outstanding
            .map { held -> held?.takeIf { it.first == phone }?.second ?: OtpRequest.Sent }
            .distinctUntilChanged()
}

/** Where the request for a code got to. */
internal sealed interface OtpRequest {

    /** Asked for, not yet answered. No code has been sent. */
    data object InFlight : OtpRequest

    /** The provider took it. A code is on its way. */
    data object Sent : OtpRequest

    /** The provider refused. Nothing was sent, and nothing was spent. */
    data class Failed(val error: DomainError) : OtpRequest

    /** The provider proved the number outright — there is no code to collect. */
    data object Verified : OtpRequest
}
