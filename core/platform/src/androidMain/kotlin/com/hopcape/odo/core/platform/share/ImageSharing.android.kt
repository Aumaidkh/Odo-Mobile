package com.hopcape.odo.core.platform.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.hopcape.odo.core.platform.file.storedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Android actual — `ACTION_SEND` with a `content://` URI, aimed at one app when it is there.
 *
 * The direct intent is tried first and the chooser is the fallback, rather than the other way
 * round: an owner who taps "Send on WhatsApp" and lands in a chooser has been given a second
 * decision they did not ask for. A phone without WhatsApp still gets one.
 *
 * The caption rides as `EXTRA_TEXT`, which WhatsApp puts in the message box beside the image.
 */
@Composable
actual fun rememberImageSharer(): (storageKey: String, caption: String, preferredApp: String?) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { storageKey, caption, preferredApp ->
            // Wrapped because there is nothing useful to say if it fails: the file went
            // missing between being written and being sent, or the phone has nowhere to
            // send it.
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}$FILE_PROVIDER_SUFFIX",
                    context.storedFile(storageKey),
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = ShareMimeType.PNG
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val direct = preferredApp?.let { Intent(send).setPackage(it) }
                // `startActivity` throws when the package is absent, which is the check —
                // `queryIntentActivities` would need the package listed in the manifest's
                // `<queries>` on Android 11 and up, and this needs no such declaration.
                val sent = direct != null && runCatching { context.start(direct) }.isSuccess
                if (!sent) context.start(Intent.createChooser(send, caption))
            }
        }
    }
}

private fun Context.start(intent: Intent) {
    // Some hosts start this from an Application context, which needs its own task. The read
    // grant rides on the chooser too — without it the app the owner picks receives a URI it
    // is not allowed to open.
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(intent)
}

/**
 * Android actual — `Bitmap.compress`, off the main thread.
 *
 * Lossless, because the card is flat colour and large type. A JPEG of it would be smaller and
 * would put artefacts around every letter of the one number the card exists to show.
 */
actual suspend fun ImageBitmap.toPngBytes(): ByteArray? = withContext(Dispatchers.Default) {
    runCatching {
        ByteArrayOutputStream().use { out ->
            asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            out.toByteArray()
        }
    }.getOrNull()
}

/** Ignored for PNG, which is lossless, and still required by the signature. */
private const val PNG_QUALITY = 100

/**
 * Appended to the package name to form the provider authority. Must match the `android:
 * authorities` in the manifest, which builds the same string from `${applicationId}`.
 */
private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
