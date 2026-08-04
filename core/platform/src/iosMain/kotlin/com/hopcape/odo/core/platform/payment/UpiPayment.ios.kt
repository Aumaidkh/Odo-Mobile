package com.hopcape.odo.core.platform.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * iOS actual — reports that there is no UPI hand-off, because there genuinely is not one.
 *
 * UPI's `upi://pay` link is an Android intent convention. The Indian payment apps on iOS
 * register their own private URL schemes (`phonepe://`, `paytmmp://`, `gpay://`) and none of
 * them accept the standard link, so there is no single thing to open. Opening a *particular*
 * app's scheme would be Odo choosing who gets the transaction, and it would fail silently for
 * every owner who uses a different one.
 *
 * Answering [UpiLaunchResult.Unsupported] is not a gap left for later: it tells the scan
 * screen to stop at showing the payment address, which the owner can then pay from their own
 * app. A launcher that pretended to work would end with a fuel fill recorded against a
 * payment nobody made.
 */
@Composable
actual fun rememberUpiPaymentLauncher(
    onResult: (UpiLaunchResult) -> Unit,
): (String) -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember { { currentOnResult(UpiLaunchResult.Unsupported) } }
}
