package com.hopcape.odo.core.platform.payment

import androidx.compose.runtime.Composable

/**
 * What came back from handing a payment link to a UPI app.
 *
 * The platform layer deliberately knows nothing about UPI beyond "open this string, hand back
 * that one". Building the link and reading the response are the domain's job
 * ([com.hopcape.odo.core.domain.payment.UpiDeepLink]), because both are a documented grammar
 * that should be tested rather than a platform capability.
 */
sealed interface UpiLaunchResult {

    /** A UPI app ran and returned [response] — the raw `txnId=…&Status=…` string. */
    data class Completed(val response: String?) : UpiLaunchResult

    /** The owner came back without the app reporting anything. Treated as cancelled. */
    data object Dismissed : UpiLaunchResult

    /** Nothing on this device can take a UPI payment, so there was nothing to open. */
    data object NoUpiApp : UpiLaunchResult

    /** The platform has no UPI hand-off at all. iOS answers this. */
    data object Unsupported : UpiLaunchResult
}

/**
 * Hands a `upi://pay?…` link to whichever app on the device can pay it, and reports back.
 *
 * A composable, like the camera and the file picker, because launching for a result needs
 * the thing hosting the UI. Returns a function to call with the link.
 *
 * On Android this opens a chooser across every installed UPI app — Odo never picks one, since
 * which app someone pays with is theirs to decide and the list changes constantly. On iOS
 * there is no equivalent: UPI apps there register private URL schemes and none of them accept
 * the standard link, so the iOS side answers [UpiLaunchResult.Unsupported] and the QR flow
 * stops at showing the owner the payment address.
 */
@Composable
expect fun rememberUpiPaymentLauncher(
    onResult: (UpiLaunchResult) -> Unit,
): (String) -> Unit
