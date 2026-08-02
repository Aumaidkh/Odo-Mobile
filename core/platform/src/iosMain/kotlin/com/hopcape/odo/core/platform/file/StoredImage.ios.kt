package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Always `null` on iOS, for the same reason [IosFileStore] stores nothing: the MVP is
 * Android-only and there is no file to read back. Callers already draw a fallback.
 */
@Composable
actual fun rememberStoredImage(storageKey: String?): ImageBitmap? = null
