package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSFileManager

/**
 * Decodes a stored file from the app's Documents directory.
 *
 * Decoding goes through Skia rather than `UIImage` because Compose draws Skia images, so going
 * via UIKit would mean re-encoding the photo only to hand it back. A file that will not decode
 * reads as "no image", which every caller already draws a fallback for.
 */
@Composable
actual fun rememberStoredImage(storageKey: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, storageKey) {
        value = storageKey?.let { key ->
            withContext(Dispatchers.Default) {
                runCatching {
                    val data = NSFileManager.defaultManager.contentsAtPath(absolutePathFor(key))
                        ?: return@runCatching null
                    Image.makeFromEncoded(data.toByteArray()).toComposeImageBitmap()
                }.getOrNull()
            }
        }
    }
    return image
}
