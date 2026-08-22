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
 * The URL is built by the shared [toMailtoUri], so this platform and Android send byte-for-
 * byte the same draft. They used to build it separately, and that is how Android ended up
 * shipping empty subjects for a year.
 */
@Composable
actual fun rememberMailComposer(): (MailDraft) -> Unit = remember {
    { draft ->
        val url = NSURL.URLWithString(draft.toMailtoUri())
        // Null when the address produced something unparseable. There is nothing to fall
        // back to and nothing useful to say, so the tap does nothing.
        if (url != null) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
}
