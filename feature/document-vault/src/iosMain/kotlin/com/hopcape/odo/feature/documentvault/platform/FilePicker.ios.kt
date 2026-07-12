package com.hopcape.odo.feature.documentvault.platform

import androidx.compose.runtime.Composable

/** iOS stub — the MVP is Android-first; a UIDocumentPickerViewController lands in Phase 2. */
@Composable
actual fun rememberFilePicker(onPicked: (String?) -> Unit): () -> Unit = {
    // TODO(iOS, Phase 2): present a UIDocumentPickerViewController and report the URL.
}
