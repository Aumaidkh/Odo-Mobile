package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes the stored file from app-private storage.
 *
 * Deliberately not an image-loading library: this shows one small local image at a time — an
 * avatar, a bill thumbnail — with no cache to manage. A decode that fails, whether the file is
 * corrupt or lost to a restore, reads as "no photo" rather than as a crash.
 *
 * The decode is bounded to [DEFAULT_TARGET_WIDTH_PX], because the callers are all small: a
 * 12 MP camera scan decoded at full size is a ~48 MB bitmap for a 72 dp thumbnail. Callers that
 * know the width they need should use [rememberStoredDocument] instead, which takes one.
 */
@Composable
actual fun rememberStoredImage(storageKey: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, storageKey) {
        value = storageKey?.let { key ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = context.storedFile(key)
                    if (file.exists()) {
                        decodeBounded(file, DEFAULT_TARGET_WIDTH_PX)?.asImageBitmap()
                    } else {
                        null
                    }
                }.getOrNull()
            }
        }
    }
    return image
}
