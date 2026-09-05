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

    /** 32^4 — one block holds this many distinct values. */
    private const val BLOCK_SPACE = 32 * 32 * 32 * 32

    private const val FNV_OFFSET_BASIS = -2128831035 // 2166136261 as a signed Int
    private const val FNV_PRIME = 16777619

    /** A second, arbitrary seed, so the two blocks are not the same hash twice. */
    private const val SECOND_SEED = 0x811C9DC5.toInt() xor 0x5BF03635

    /**
     * Two blocks, each a hash of the whole id.
     *
     * Hashed rather than sliced. Mapping the id's own characters onto a 32-letter alphabet
     * folds distinct ones together — `P` and `G` land on the same letter — so two ids
     * differing only in such a pair would share a reference, and the id is a plain string
     * that nothing constrains to hex. Every character of it reaches both blocks, and an id
     * shorter than the blocks needs no padding rule.
     *
     * FNV-1a, the same as `DiagnosticReference`. Small, stable across platforms, and not a
     * security claim — the row keeps the id, and this is a label for a phone call.
     */
    fun of(id: SupportTicketId): String {
        val first = encode(hash(id.value, FNV_OFFSET_BASIS))
        val second = encode(hash(id.value, SECOND_SEED))
        return "$PREFIX-$first-$second"
    }

    private fun hash(value: String, seed: Int): Int {
        var hash = seed
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
