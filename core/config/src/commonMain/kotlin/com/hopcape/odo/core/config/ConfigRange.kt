package com.hopcape.odo.core.config

/**
 * Parses the `"min..max"` form used by [Value.range].
 *
 * Both ends are inclusive. The processor checks at build time that a range parses and
 * contains its default, so a malformed range cannot reach a shipped build; if one
 * somehow does, it is treated as no range rather than rejecting every value.
 */
internal object ConfigRange {

    private const val SEPARATOR = ".."

    /**
     * Whether [value] falls inside [range]. Comparison is done as `Double`, which is
     * exact for every `Int` and for the `Long` magnitudes a config key holds.
     */
    fun contains(range: String?, value: Any): Boolean {
        if (range.isNullOrBlank()) return true
        val number = (value as? Number)?.toDouble() ?: return true
        val separator = range.indexOf(SEPARATOR)
        if (separator <= 0) return true
        val min = range.substring(0, separator).trim().toDoubleOrNull() ?: return true
        val max = range.substring(separator + SEPARATOR.length).trim().toDoubleOrNull() ?: return true
        return number in min..max
    }
}
