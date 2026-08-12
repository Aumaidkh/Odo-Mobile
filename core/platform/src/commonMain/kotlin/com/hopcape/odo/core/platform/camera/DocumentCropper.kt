package com.hopcape.odo.core.platform.camera

/**
 * Crops a captured photo to a detected paper outline, straightening the perspective.
 *
 * Takes and answers storage keys, so above this the cropped photo is the same kind of
 * thing as the original — the review screen shows it, the extractor reads it, sync uploads
 * it, none of them knowing a crop happened. On top of the cleaner preview, the crop is
 * what removes everything *around* the bill before OCR sees it.
 *
 * Best effort by contract: any failure — a file that will not decode, a degenerate quad —
 * answers the **original** key. A missed crop costs a wider photo; a thrown exception here
 * would cost the capture, and the photo is the thing that cannot be re-made.
 */
fun interface DocumentCropper {

    /** The cropped copy's storage key, or [storageKey] itself when cropping was not possible. */
    suspend fun crop(storageKey: String, quad: DetectedQuad): String
}
