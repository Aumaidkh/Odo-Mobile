package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Reads an image the app stored earlier, by the key [PlatformFileStore.save] handed back.
 *
 * Returns `null` while the file is being read, and also when there is no key, the file is
 * gone, or the platform cannot decode it. A caller therefore always needs something to show
 * without it — the profile falls back to the monogram avatar.
 *
 * Decoding happens off the main thread; the composable recomposes when the image is ready.
 */
@Composable
expect fun rememberStoredImage(storageKey: String?): ImageBitmap?
