package com.hopcape.odo.web.core.platform

/**
 * A file on its way in, as the browser handed it over.
 *
 * Carries the bytes rather than a browser `File` so that everything downstream of
 * it stays common code — reading the file is the browser's job and happens at
 * [pickImage], the one place that already knows it is running in a browser.
 *
 * Here rather than in either app's domain because it is what [pickImage] produces,
 * and a type produced by shared code cannot live in one of the two things that
 * consume it.
 */
data class UploadRequest(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    // Data classes compare arrays by reference, which would make two identical
    // uploads unequal and one re-read of the same file equal to nothing.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is UploadRequest && name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int =
        (name.hashCode() * 31 + mimeType.hashCode()) * 31 + bytes.contentHashCode()
}
