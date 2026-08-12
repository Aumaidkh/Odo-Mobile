package com.hopcape.odo.core.platform.share

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual — `ACTION_SEND` through a chooser.
 *
 * A chooser rather than a resolved package: which app someone shares with is theirs to
 * decide, and the installed set differs on every phone.
 *
 * The launch is wrapped because `startActivity` from a non-Activity context can throw on
 * devices with no app able to take plain text at all. There is nothing useful to tell the
 * owner in that case — they asked to share and the phone has nowhere to share to — so it
 * fails quietly rather than crashing a screen they were only reading.
 */
@Composable
actual fun rememberTextSharer(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, null).apply {
                // The chooser is started from an Application context in some hosts, which
                // requires its own task.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
    }
}
