package com.hopcape.odo.core.platform.file

/**
 * Draws the first page of a stored document as an image.
 *
 * Text recognition takes a picture, and the papers an owner uploads are usually PDFs — an
 * insurance policy is almost always emailed as one. Without this, an uploaded policy has no
 * image to read and its expiry date can never be found.
 *
 * Only the first page: every date a vehicle paper carries is printed on its front sheet, and
 * rendering the rest would cost time for text nobody reads.
 */
fun interface StoredPageRenderer {

    /**
     * The first page of the file at [storageKey], as encoded PNG bytes.
     *
     * Null when the file is missing, is not a page-based document, or the platform has no
     * renderer for it. The caller decides what that means; here it is simply "no image".
     */
    suspend fun firstPageAsPng(storageKey: String): ByteArray?
}
