package com.hopcape.odo.core.platform.file

import android.content.Context
import java.io.File

/**
 * The file a storage key names — the one place that knows keys live under `filesDir`.
 *
 * Every consumer of a stored file (the store itself, the capture cropper) resolves through
 * here, so if the root ever moves there is one line to change instead of a scattered
 * convention that degrades quietly.
 */
internal fun Context.storedFile(key: String): File = File(filesDir, key)
