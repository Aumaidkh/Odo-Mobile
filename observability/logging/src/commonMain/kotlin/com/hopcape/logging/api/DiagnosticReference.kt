package com.hopcape.logging.api

/**
 * The short code a support ticket and its uploaded logs are both filed under.
 *
 * Support reads it out of an email and searches for it; the same string is stored on the
 * `log_uploads` row of every file the request covers. Without it an upload is an orphan —
 * it arrives, and nobody can tell which ticket it belongs to.
 *
 * The shape is `ODO-AB12-CD34`: the first block identifies the installation, the second the
 * moment the request was made. Both are Crockford's base 32, which leaves out I, L, O and U
 * so a code read over the phone cannot be heard as a different one.
 *
 * The install block is a hash, not a slice of the id: two installations whose ids happen to
 * start alike must not share a block. It is not reversible, and does not need to be — the
 * server stores the installation id beside the reference.
 */
@StableLoggerApi
object DiagnosticReference {

    /** Crockford's base 32: no I, L, O or U, so nothing reads as a 1 or a 0. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val BLOCK_LENGTH = 4
    private const val PREFIX = "ODO"

    /** 32^4 — one block holds this many distinct values. */
    private const val BLOCK_SPACE = 32 * 32 * 32 * 32

    private const val FNV_OFFSET_BASIS = -2128831035 // 2166136261 as a signed Int
    private const val FNV_PRIME = 16777619

    /**
     * The reference for a diagnostics request made at [atEpochMs] on the installation
     * [installationId] names.
     *
     * The time block counts seconds, so it repeats about every 12 days. That is not a
     * uniqueness problem: a repeat needs the same installation to file two requests exactly
     * that far apart, and both rows still carry their own timestamp.
     */
    fun create(installationId: String, atEpochMs: Long): String {
        val installBlock = encode(hash(installationId))
        val timeBlock = encode(((atEpochMs / 1000L) % BLOCK_SPACE).toInt())
        return "$PREFIX-$installBlock-$timeBlock"
    }

    /** FNV-1a, folded into the block's range. Small, stable, and not a security claim. */
    private fun hash(value: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (char in value) {
            hash = (hash xor char.code) * FNV_PRIME
        }
        // Positive first, then folded: a negative remainder would index outside the alphabet.
        return ((hash.toLong() and 0xFFFFFFFFL) % BLOCK_SPACE).toInt()
    }

    private fun encode(value: Int): String {
        val chars = CharArray(BLOCK_LENGTH)
        var remaining = value
        for (index in BLOCK_LENGTH - 1 downTo 0) {
            chars[index] = ALPHABET[remaining % ALPHABET.length]
            remaining /= ALPHABET.length
        }
        return chars.concatToString()
    }
}
