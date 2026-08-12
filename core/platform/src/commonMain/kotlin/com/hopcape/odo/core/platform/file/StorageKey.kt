package com.hopcape.odo.core.platform.file

/**
 * Builds the relative key a stored file lives at. It sits in common code so the platforms
 * cannot disagree about where the same file belongs.
 */
object StorageKey {

    /** Used when the picked file's type cannot be established. */
    const val FALLBACK_EXTENSION = "bin"

    private const val MAX_EXTENSION_LENGTH = 5

    /**
     * `directory/fileName.ext`.
     *
     * [rawExtension] is whatever the platform could work out — a MIME lookup, a filename
     * suffix, or nothing. It is normalized here: lowercased, stripped of a leading dot, and
     * rejected unless it is short and alphanumeric. A filename is being built from it, so
     * `../..` must never survive that check.
     */
    fun of(directory: String, fileName: String, rawExtension: String?): String =
        "$directory/$fileName.${normalizeExtension(rawExtension)}"

    private fun normalizeExtension(raw: String?): String {
        val candidate = raw?.trim()?.removePrefix(".")?.lowercase().orEmpty()
        val usable = candidate.isNotEmpty() &&
            candidate.length <= MAX_EXTENSION_LENGTH &&
            candidate.all { it.isLetterOrDigit() }
        return if (usable) candidate else FALLBACK_EXTENSION
    }
}
