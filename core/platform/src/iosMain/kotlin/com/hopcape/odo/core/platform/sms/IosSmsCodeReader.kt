package com.hopcape.odo.core.platform.sms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * iOS has no SMS retriever, and needs none.
 *
 * The system keyboard already offers a one-time code from the notification banner as a
 * suggestion above the keys, without the app being involved or asking for anything. Reporting
 * [SmsCodeStatus.Unsupported] is not a gap — it tells the screen to stop showing an
 * auto-reading affordance that would be duplicating what iOS does better.
 *
 * The one thing that makes it work is on the field, not here: `textContentType = .oneTimeCode`,
 * which Compose's OTP field maps from its keyboard options.
 */
internal class IosSmsCodeReader : SmsCodeReader {
    override fun listen(): Flow<SmsCodeStatus> = flowOf(SmsCodeStatus.Unsupported)
}

/** Nothing on iOS is delivered against a signature hash, so there is none to report. */
internal class IosSmsAppSignature : SmsAppSignature {
    override suspend fun current(): List<String> = emptyList()
}
