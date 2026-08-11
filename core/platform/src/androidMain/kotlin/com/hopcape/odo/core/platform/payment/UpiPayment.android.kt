package com.hopcape.odo.core.platform.payment

import android.app.Activity
import android.content.Context
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
 * gets the transaction. That holds for the fallback links too — they widen what is offered,
 * they do not narrow it to one.
 */
@Composable
actual fun rememberUpiPaymentLauncher(
    onResult: (UpiLaunchResult) -> Unit,
): (List<String>) -> Unit {
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
        { links ->
            val intents = links.map { Intent(Intent.ACTION_VIEW, it.toUri()) }
            val standard = intents.first()

            // Asking the package manager decides *which* link to send, never whether to send
            // one. Package visibility filters what this query can see, but it does not filter
            // starting an implicit intent — the system resolves that against every app on the
            // phone. So when nothing answers, the standard link still goes out unwrapped and
            // the platform gets the final say; only the ActivityNotFoundException it throws
            // when nothing can pay counts as "no UPI app". A missing <queries> entry then
            // costs the chooser, not the payment.
            // Sent bare, not wrapped in a chooser. The owner still chooses — the system shows
            // its own picker whenever more than one app can pay, and goes straight to the one
            // they made default otherwise. What that buys is the hand-off the payment apps
            // expect: an activity started for a result by the app that wants paying, with
            // nothing standing between the two. A chooser is another activity in the middle,
            // and a payment app that cannot see who is asking has every reason to refuse.
            val toLaunch = when {
                standard.resolves(context) -> standard
                else -> {
                    val fallbacks = intents.drop(1).filter { it.resolves(context) }
                    when {
                        fallbacks.isEmpty() -> standard
                        fallbacks.size == 1 -> fallbacks.single()
                        // Several app-specific links answered and no shared one did, so there
                        // is no single intent that offers them all. A chooser is the only way
                        // left to put the choice back in the owner's hands.
                        else -> Intent.createChooser(fallbacks.first(), null).apply {
                            putExtra(Intent.EXTRA_INITIAL_INTENTS, fallbacks.drop(1).toTypedArray())
                        }
                    }
                }
            }

            runCatching { launcher.launch(toLaunch) }
                .onFailure { currentOnResult(UpiLaunchResult.NoUpiApp) }
        }
    }
}

/** Whether anything this app is allowed to see can open this intent. */
private fun Intent.resolves(context: Context): Boolean =
    resolveActivity(context.packageManager) != null

/** Where every UPI app puts its `txnId=…&Status=…` answer. Fixed by the NPCI spec. */
private const val RESPONSE_EXTRA = "response"
