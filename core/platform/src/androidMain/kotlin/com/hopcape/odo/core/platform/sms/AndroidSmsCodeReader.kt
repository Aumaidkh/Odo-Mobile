package com.hopcape.odo.core.platform.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Play Services' SMS Retriever, as a flow.
 *
 * No permission is asked for and none is needed. The system delivers exactly one message — the
 * one whose body carries this build's signature hash — and the app never gains the ability to
 * read anything else. That is the entire reason to use this rather than `READ_SMS`, which would
 * ask to read every message the owner has ever received in order to fill in six digits.
 *
 * `RECEIVER_EXPORTED` is required and is not a mistake: the sender is Play Services, a
 * different process, so the receiver has to be reachable from outside the app. It is protected
 * instead by `SEND_PERMISSION`, which only Play Services holds, so nothing else on the device
 * can deliver to it.
 */
internal class AndroidSmsCodeReader(private val context: Context) : SmsCodeReader {

    override fun listen(): Flow<SmsCodeStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return

                @Suppress("DEPRECATION")
                val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return

                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                        // The message is a sentence with a code in it, not a code. Pulling the
                        // digits out here keeps the shape of the copy the provider's business.
                        message?.extractCode()?.let { trySend(SmsCodeStatus.Received(it)) }
                    }

                    // The retriever's own five-minute window, not a failure. SMS is often slow
                    // and the owner may simply be typing it themselves.
                    CommonStatusCodes.TIMEOUT -> trySend(SmsCodeStatus.TimedOut)
                    else -> trySend(SmsCodeStatus.Unsupported)
                }
            }
        }

        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, SmsRetriever.SEND_PERMISSION, null, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter, SmsRetriever.SEND_PERMISSION, null)
        }

        trySend(SmsCodeStatus.Listening)

        // Reports Unsupported rather than throwing: a device with no Play Services is a
        // device where the owner types the code, which is not an error state.
        SmsRetriever.getClient(context).startSmsRetriever()
            .addOnFailureListener { trySend(SmsCodeStatus.Unsupported) }

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /**
     * The code inside the message.
     *
     * A run of digits of the expected length, rather than a position or a full template match:
     * the copy around it is the provider's to change, and a parser tied to that copy breaks
     * the first time marketing rewords the SMS.
     */
    private fun String.extractCode(): String? =
        Regex("\\b(\\d{$CODE_LENGTH})\\b").find(this)?.groupValues?.get(1)

    private companion object {
        /** Supabase issues six-digit codes. */
        const val CODE_LENGTH = 6
    }
}
