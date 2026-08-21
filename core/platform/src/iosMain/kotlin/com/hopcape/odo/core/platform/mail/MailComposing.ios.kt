package com.hopcape.odo.core.platform.mail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS actual — a `mailto:` URL handed to the system.
 *
 * `mailto:` rather than `MFMailComposeViewController`, which is the other way to do this on
 * iOS. The controller only ever composes in Apple Mail, so someone whose mail lives in
 * Gmail or Outlook gets a compose sheet for an account they do not use. Opening the URL
 * goes to whichever mail app they set as the default.
 *
 * Every field is percent-encoded first. Report bodies contain newlines and `&`, and both
 * end the draft early if they reach the URL raw.
 */
@Composable
actual fun rememberMailComposer(): (MailDraft) -> Unit = remember {
    { draft ->
        val url = NSURL.URLWithString(
            "mailto:${draft.to.percentEncoded()}" +
                "?subject=${draft.subject.percentEncoded()}" +
                "&body=${draft.body.percentEncoded()}",
        )
        // Null when the address produced something unparseable. There is nothing to fall
        // back to and nothing useful to say, so the tap does nothing.
        if (url != null) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
}

/**
 * Percent-encodes one `mailto:` field, per RFC 3986.
 *
 * Written out rather than called through `NSString`, so this file needs no cinterop and
 * behaves the same as the shared code around it. Everything outside the unreserved set is
 * escaped, which is stricter than necessary but never wrong: `&` and `=` would otherwise
 * split the URL into extra parameters, and a multi-line body would truncate at the newline.
 *
 * Encoding the UTF-8 bytes rather than the characters is what makes a body in any script
 * survive the trip.
 */
private fun String.percentEncoded(): String = buildString {
    for (byte in this@percentEncoded.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        val unreserved = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.~"
        if (unreserved) {
            append(char)
        } else {
            append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
