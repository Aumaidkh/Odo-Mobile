package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable

/**
 * Not implemented on iOS.
 *
 * The MVP ships Android only, and nothing in the iOS entry point opens a document viewer, so
 * this answers [StoredDocument.Unsupported] rather than carrying a PDFKit path that no screen
 * reaches. iOS gets a real implementation when iOS gets a UI.
 */
@Composable
actual fun rememberStoredDocument(storageKey: String?): StoredDocument =
    if (storageKey == null) StoredDocument.Missing else StoredDocument.Unsupported
