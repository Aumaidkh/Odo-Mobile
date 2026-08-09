package com.hopcape.odo.core.platform.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** The width a page is rendered at. Wide enough that printed dates survive recognition. */
private const val PAGE_WIDTH_PX = 2048

/** PNG ignores this, but the API demands a number. */
private const val PNG_QUALITY = 100

/**
 * Android's renderer, on the platform's own [PdfRenderer]. No dependency needed: it has been
 * in Android since API 21 and the app's floor is 26.
 *
 * Everything that can go wrong here is about the owner's file — a truncated download, a
 * password-protected policy, a `.pdf` that is not one — and none of it is something the app
 * can fix. So every failure answers null and the caller says "we could not read this" rather
 * than crashing on a file it was handed.
 */
internal class AndroidStoredPageRenderer(private val context: Context) : StoredPageRenderer {

    override suspend fun firstPageAsPng(storageKey: String): ByteArray? {
        if (StoredFileKinds.of(storageKey) != StoredFileKind.PDF) return null
        val file = context.storedFile(storageKey)
        if (!file.exists()) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (renderer.pageCount == 0) return@use null
                        renderer.openPage(0).use { page ->
                            val height = (PAGE_WIDTH_PX * page.height.toFloat() / page.width)
                                .roundToInt()
                                .coerceAtLeast(1)
                            Bitmap.createBitmap(PAGE_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
                                .apply {
                                    // A PDF paints only its ink. Anything it never draws on
                                    // stays transparent, which recognition reads as black
                                    // paper with black text on it.
                                    eraseColor(Color.WHITE)
                                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                                .toPng()
                        }
                    }
                }
            }.getOrNull()
        }
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        out.toByteArray()
    }
}
