package com.hopcape.odo.core.platform.store

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual — the Play Store listing for the installed package.
 *
 * `market://` first, so the Play app opens the listing directly. A device without Play
 * installed, or with it disabled, throws on that intent and gets the browser URL instead.
 * Reading the package name from the Context rather than hardcoding it keeps the debug and
 * release application IDs pointing at their own listings.
 */
@Composable
actual fun rememberStoreRater(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        {
            val packageName = context.packageName
            val open = { uri: String ->
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            runCatching { open("market://details?id=$packageName") }
                .recoverCatching { open("https://play.google.com/store/apps/details?id=$packageName") }
        }
    }
}
