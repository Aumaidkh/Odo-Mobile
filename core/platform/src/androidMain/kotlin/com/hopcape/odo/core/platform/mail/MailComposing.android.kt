package com.hopcape.odo.core.platform.mail

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual — `ACTION_SENDTO` on a `mailto:` URI.
 *
 * `ACTION_SENDTO` rather than `ACTION_SEND`: the latter offers every app that can take text,
 * so a message addressed to a support mailbox would come back as a chooser full of
 * messengers and note apps. Restricting to the `mailto:` scheme means only mail apps match.
 *
 * The recipient travels in the URI and the subject and body in extras, which is the pairing
 * mail apps handle most consistently. Putting the subject in the URI query as well makes
 * some clients show it twice.
 *
 * Seeing the installed mail apps at all needs the `<queries>` entry in the app manifest,
 * added alongside this. Without it Android 11 and later hide them, and the intent resolves
 * to nothing on a phone that does have mail.
 */
@Composable
actual fun rememberMailComposer(): (MailDraft) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { draft ->
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${Uri.encode(draft.to)}")
                putExtra(Intent.EXTRA_SUBJECT, draft.subject)
                putExtra(Intent.EXTRA_TEXT, draft.body)
                // The composer is started from an Application context in some hosts, which
                // requires its own task.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
