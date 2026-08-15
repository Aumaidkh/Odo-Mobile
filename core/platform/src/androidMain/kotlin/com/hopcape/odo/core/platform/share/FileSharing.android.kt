package com.hopcape.odo.core.platform.share

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.hopcape.odo.core.platform.file.storedFile

/**
 * Android actual — `ACTION_SEND` with a `content://` URI, through a chooser.
 *
 * A `FileProvider` URI rather than a `file://` one: the document lives in the app's private
 * storage, so the receiving app cannot open a path, and handing one over throws
 * `FileUriExposedException` on anything since Android 7 anyway. The provider grants read
 * access to that single URI for the life of the receiving activity, and to nothing else.
 *
 * The authority is derived from the package name so the debug, stage and release builds each
 * declare their own — two variants sharing one authority cannot both be installed.
 */
@Composable
actual fun rememberFileSharer(): (storageKey: String, mimeType: String, title: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { storageKey, mimeType, title ->
            // Wrapped because there is nothing useful to say if it fails: the owner asked to
            // share and the phone has nowhere to share to, or the file went missing between
            // being rendered and being sent.
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}$FILE_PROVIDER_SUFFIX",
                    context.storedFile(storageKey),
                )

                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    // Read by mail clients as the subject, and by some file targets as the
                    // suggested name — otherwise the record arrives called "attachment".
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(send, title).apply {
                    // Some hosts start the chooser from an Application context, which needs
                    // its own task.
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // The grant rides on the chooser too: without this the app the owner
                    // picks receives a URI it is not allowed to open.
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
            }
        }
    }
}

/**
 * Appended to the package name to form the provider authority. Must match the `android:
 * authorities` in the manifest, which builds the same string from `${applicationId}`.
 */
private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
