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
 * Hands a payment to whichever app on the device can take it, and reports back.
 *
 * A composable, like the camera and the file picker, because launching for a result needs
 * the thing hosting the UI. Returns a function to call with the links.
 *
 * **Links, plural, best first.** The caller passes every link that would pay this one
 * payment — the standard `upi://pay` one and the payment apps' own schemes behind it. The
 * platform tries them in order and offers everything that answers; it does not know or care
 * what the difference between them is, which is the point. Building them is the domain's job
 * ([com.hopcape.odo.core.domain.payment.UpiDeepLink.candidates]), and the reason there is
 * more than one is documented there.
 *
 * On Android this opens a chooser — Odo never picks the app, since which app someone pays
 * with is theirs to decide and the list changes constantly. On iOS there is no equivalent:
 * the apps there register private URL schemes, none accepts the standard link, and none hands
 * a result back, so the iOS side answers [UpiLaunchResult.Unsupported] and the QR flow stops
 * at showing the owner the payment address.
 */
@Composable
expect fun rememberUpiPaymentLauncher(
    onResult: (UpiLaunchResult) -> Unit,
): (List<String>) -> Unit
