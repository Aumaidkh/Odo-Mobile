package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A stored file as something that can be drawn: a sequence of pages.
 *
 * An image is a document with one page, a PDF has as many as it has. Callers therefore never
 * branch on the file's kind, which is what lets a new kind arrive as another implementation
 * rather than an edit to every viewer.
 *
 * Pages render on demand rather than up front. A twenty-page insurance PDF decoded in one go
 * is tens of megabytes of bitmap for the one page the owner is looking at.
 */
@Stable
interface DocumentPages {

    /** How many pages there are. Always at least 1 for a document that opened. */
    val count: Int

    /**
     * Draw page [index] at roughly [targetWidthPx] — normally the width it is about to be
     * laid out at, so a 12 MP photo is not decoded at full size to fill a phone screen.
     *
     * Returns `null` when that page cannot be drawn (a corrupt page, a decode that ran out of
     * memory). The caller shows a placeholder for that page; the rest of the document is
     * still readable, which is better than failing the whole file.
     */
    suspend fun render(index: Int, targetWidthPx: Int): ImageBitmap?
}

/** What [rememberStoredDocument] found at a storage key. */
@Immutable
sealed interface StoredDocument {

    /** The file is being opened. Every document starts here. */
    data object Loading : StoredDocument

    /**
     * There is no file at the key. Either none was given, or the file is gone — a restore
     * from backup brings the database back without app-private storage.
     */
    data object Missing : StoredDocument

    /** The file is there but this platform cannot draw it. */
    data object Unsupported : StoredDocument

    data class Ready(val pages: DocumentPages) : StoredDocument
}

/**
 * Opens the file at [storageKey] for display, by the key [PlatformFileStore.save] handed back.
 *
 * The file is opened off the main thread and closed when the composable leaves, so a PDF does
 * not hold a file descriptor open after the screen it was shown on is gone.
 */
@Composable
expect fun rememberStoredDocument(storageKey: String?): StoredDocument
