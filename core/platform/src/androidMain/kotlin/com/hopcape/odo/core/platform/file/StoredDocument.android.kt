package com.hopcape.odo.core.platform.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/** Widest a page is ever decoded, whatever width the caller asks for. */
private const val MAX_PAGE_WIDTH_PX = 2048

@Composable
actual fun rememberStoredDocument(storageKey: String?): StoredDocument {
    val context = LocalContext.current
    val document by produceState<StoredDocument>(StoredDocument.Loading, storageKey, context) {
        val opened = if (storageKey == null) {
            StoredDocument.Missing
        } else {
            withContext(Dispatchers.IO) { openStoredDocument(context, storageKey) }
        }
        value = opened
        // A PDF holds a file descriptor open for as long as it can be paged through, so the
        // document is closed when the screen showing it goes away rather than at some later
        // garbage collection.
        awaitDispose { (opened as? StoredDocument.Ready)?.pages?.let { (it as ClosablePages).close() } }
    }
    return document
}

/**
 * Opens the file the key names, choosing how to draw it from the key's extension.
 *
 * A PDF that will not open reads as [StoredDocument.Unsupported] rather than as a crash: the
 * file is on the owner's device and the app cannot do anything about it being unreadable, so
 * the screen says so and the rest of the vault keeps working.
 */
private fun openStoredDocument(context: Context, storageKey: String): StoredDocument {
    val file = context.storedFile(storageKey)
    if (!file.exists()) return StoredDocument.Missing
    return when (StoredFileKinds.of(storageKey)) {
        StoredFileKind.IMAGE -> StoredDocument.Ready(ImagePages(file))
        StoredFileKind.PDF -> runCatching { PdfPages(file) }
            .fold(
                onSuccess = { pages ->
                    if (pages.count > 0) {
                        StoredDocument.Ready(pages)
                    } else {
                        pages.close()
                        StoredDocument.Unsupported
                    }
                },
                onFailure = { StoredDocument.Unsupported },
            )

        StoredFileKind.UNSUPPORTED -> StoredDocument.Unsupported
    }
}

/** Pages that hold something to release. Android-only, so the common interface stays plain. */
private interface ClosablePages : DocumentPages, Closeable

/** An image: one page, nothing to close. */
private class ImagePages(private val file: File) : ClosablePages {

    override val count: Int = 1

    override suspend fun render(index: Int, targetWidthPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching { decodeBounded(file, boundedWidth(targetWidthPx))?.asImageBitmap() }.getOrNull()
        }

    override fun close() = Unit
}

/**
 * A PDF, drawn page by page through the platform renderer. No dependency is needed for this —
 * [PdfRenderer] has been in Android since API 21 and the app's floor is 26.
 */
private class PdfPages(file: File) : ClosablePages {

    private val descriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private val renderer = runCatching { PdfRenderer(descriptor) }
        .getOrElse { descriptor.close(); throw it }

    /** [PdfRenderer] allows one open page at a time, so renders are serialized. */
    private val lock = Mutex()

    override val count: Int = renderer.pageCount

    override suspend fun render(index: Int, targetWidthPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    renderer.openPage(index).use { page ->
                        val width = boundedWidth(targetWidthPx)
                        val height = (width * page.height.toFloat() / page.width)
                            .roundToInt()
                            .coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                            // A PDF paints only its ink; anything it never draws on stays
                            // transparent, which on the viewer's dark surface means the page
                            // is black-on-black. Paper is what the owner expects to see.
                            eraseColor(Color.WHITE)
                            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }.asImageBitmap()
                    }
                }.getOrNull()
            }
        }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

/** The width to decode at: what the caller asked for, kept sane at both ends. */
private fun boundedWidth(targetWidthPx: Int): Int =
    if (targetWidthPx <= 0) DEFAULT_TARGET_WIDTH_PX else targetWidthPx.coerceAtMost(MAX_PAGE_WIDTH_PX)
