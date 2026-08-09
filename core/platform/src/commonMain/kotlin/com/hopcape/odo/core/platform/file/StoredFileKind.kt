package com.hopcape.odo.core.platform.file

/** What kind of file a storage key names, as far as displaying it goes. */
enum class StoredFileKind { IMAGE, PDF, UNSUPPORTED }

/**
 * Works out a stored file's kind from its key alone.
 *
 * The key already carries the extension ([StorageKey.of] put it there), so nothing has to read
 * the file or keep a MIME type in the database to know how to open it. It sits in common code
 * so the platforms cannot disagree about what counts as an image.
 *
 * Public because screens ask it too: a card showing a stored file picks its icon and its
 * "PDF" badge from this rather than parsing the key a second time.
 */
object StoredFileKinds {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    private const val PDF_EXTENSION = "pdf"

    fun of(storageKey: String): StoredFileKind = when (storageKey.extension()) {
        in IMAGE_EXTENSIONS -> StoredFileKind.IMAGE
        PDF_EXTENSION -> StoredFileKind.PDF
        else -> StoredFileKind.UNSUPPORTED
    }

    /**
     * The extension of the key's last segment, lowercased; empty when it has none.
     *
     * The last segment first, because a directory may contain a dot while the file does not —
     * `documents/a.b/c` has no extension.
     */
    private fun String.extension(): String =
        substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "").lowercase()
}
