package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable

/** iOS stub — the MVP is Android-first; a UIDocumentPickerViewController lands in Phase 2. */
@Composable
actual fun rememberFilePicker(
    mimeTypes: List<String>,
    onPicked: (String?) -> Unit,
): () -> Unit = {
    // TODO(iOS, Phase 2): present a UIDocumentPickerViewController and report the URL.
}
