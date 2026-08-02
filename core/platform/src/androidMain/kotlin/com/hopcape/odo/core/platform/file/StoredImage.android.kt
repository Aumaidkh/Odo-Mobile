package com.hopcape.odo.core.platform.file

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decodes the stored file from app-private storage.
 *
 * Deliberately not an image-loading library: the app shows exactly one stored image (the
 * profile photo), it is small, and it is on local disk. A decode that fails — a corrupt
 * file, or one lost to a restore — reads as "no photo" rather than as a crash.
 */
@Composable
actual fun rememberStoredImage(storageKey: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, storageKey) {
        value = storageKey?.let { key ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.filesDir, key)
                    if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
                }.getOrNull()
            }
        }
    }
    return image
}
