package com.hopcape.odo.core.domain.support

/**
 * The short code the owner is shown and reads back to support — `ODO-8F42-19C7`.
 *
 * Derived from the ticket's own id rather than stored beside it: two columns holding the same
 * fact are two columns that can disagree, and this one has to survive a reinstall and match
 * whatever the panel computes from the same row.
 *
 * The alphabet is Crockford's base 32, which leaves out I, L, O and U so a code read over the
 * phone cannot be heard as a different one. It is the same shape `DiagnosticReference` uses;
 * that one lives in `:observability:logging`, which the shared kernel may not depend on, and
 * a code an owner reads aloud is worth the twenty lines twice.
 */
object TicketReference {

    private const val PREFIX = "ODO"
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val BLOCK_LENGTH = 4

    /**
     * Two blocks, taken from the id.
     *
     * The id is already random, so nothing needs hashing — the blocks are its own characters
     * mapped onto the alphabet. An id shorter than the two blocks is padded rather than
     * refused: a reference is a label, and a screen that fails to show one is worse than a
     * short one.
     */
    fun of(id: SupportTicketId): String {
        val source = id.value.filter { it.isLetterOrDigit() }.uppercase()
        val first = block(source, 0)
        val second = block(source, BLOCK_LENGTH)
        return "$PREFIX-$first-$second"
    }

    private fun block(source: String, from: Int): String = buildString {
        for (offset in 0 until BLOCK_LENGTH) {
            val char = source.getOrNull(from + offset) ?: '0'
            append(ALPHABET[char.code % ALPHABET.length])
        }
    }
}
