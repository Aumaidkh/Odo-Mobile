package com.hopcape.odo.core.platform.camera

/**
 * Reads a QR code out of a still photo.
 *
 * The live preview already reads codes off its frames
 * ([CameraFrameAnalysis.Qr]); this is the same job for a picture the owner picked from
 * their gallery — a payment code screenshotted or sent over WhatsApp, which never passes in
 * front of the camera at all.
 *
 * Takes a storage key rather than bytes, so callers hand it the same thing they hand every
 * other file API. `null` means no code was found, which is an answer rather than a failure:
 * the screen says the picture holds no payment code and the owner picks another.
 */
fun interface QrImageDecoder {

    /** The payload of the first code found in the stored image, or `null` if there is none. */
    suspend fun decode(storageKey: String): String?
}
