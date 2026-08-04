package com.hopcape.odo.core.platform.payment

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/**
 * Android actual — opens the UPI link through a chooser and reads the app's response.
 *
 * `startActivityForResult` rather than a plain `startActivity`, because the whole point is
 * the answer: a fuel fill may only be recorded against a payment that actually succeeded, and
 * a fire-and-forget launch would leave the app guessing.
 *
 * A chooser rather than a specific package. Which app someone pays with is theirs to choose,
 * the set installed varies with every phone, and hard-coding one would be Odo deciding who
 * gets the transaction.
 */
@Composable
actual fun rememberUpiPaymentLauncher(
    onResult: (UpiLaunchResult) -> Unit,
): (String) -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        // A cancelled result is not always a refusal: several UPI apps finish with
        // RESULT_CANCELED while still putting the outcome in the extras, so the response is
        // read either way and only a genuinely empty one counts as dismissed.
        val response = result.data?.getStringExtra(RESPONSE_EXTRA)
        currentOnResult(
            when {
                response != null -> UpiLaunchResult.Completed(response)
                result.resultCode == Activity.RESULT_OK -> UpiLaunchResult.Completed(null)
                else -> UpiLaunchResult.Dismissed
            },
        )
    }

    return remember(context, launcher) {
        { link ->
            val payment = Intent(Intent.ACTION_VIEW, link.toUri())
            if (payment.resolveActivity(context.packageManager) == null) {
                currentOnResult(UpiLaunchResult.NoUpiApp)
            } else {
                runCatching { launcher.launch(Intent.createChooser(payment, null)) }
                    .onFailure { currentOnResult(UpiLaunchResult.NoUpiApp) }
            }
        }
    }
}

/** Where every UPI app puts its `txnId=…&Status=…` answer. Fixed by the NPCI spec. */
private const val RESPONSE_EXTRA = "response"
