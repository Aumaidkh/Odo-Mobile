package com.hopcape.odo.core.platform.file

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Android actual — the system document picker, filtered to the caller's MIME types. */
@Composable
actual fun rememberFilePicker(
    mimeTypes: List<String>,
    onPicked: (String?) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPicked(uri?.toString())
    }
    val types = remember(mimeTypes) { mimeTypes.toTypedArray() }
    return { launcher.launch(types) }
}
