package com.hopcape.odo.core.config.processor

/**
 * Parsing of the two string-encoded parts of a declaration: `@Value.default` and `range`.
 *
 * Both are strings at the declaration site because a Kotlin annotation parameter has one
 * type and `@Value` has to carry `Int`, `Long`, `Double` and `String` defaults. Everything
 * here runs at build time, so a bad declaration is a compile error rather than a surprise
 * on a device.
 */
internal object Defaults {

    private const val SEPARATOR = ".."

    /**
     * Turns a declared default into a Kotlin source literal of [type], or null when it does
     * not parse. For an enum the raw value is matched against the constant names, ignoring
     * case, and the canonical name is what comes back — so `"off"` is accepted for a
     * constant called `OFF`, which is how a remote console usually spells it.
     */
    fun literal(raw: String, type: KeyType, enumConstants: List<String>): String? = when (type) {
        KeyType.BOOLEAN -> raw.toBooleanStrictOrNull()?.toString()
        KeyType.INT -> raw.toIntOrNull()?.toString()
        KeyType.LONG -> raw.toLongOrNull()?.let { "${it}L" }
        KeyType.DOUBLE -> raw.toDoubleOrNull()?.toString()
        KeyType.STRING -> "\"${raw.escaped()}\""
        KeyType.ENUM -> enumConstants.firstOrNull { it.equals(raw, ignoreCase = true) }
            ?.let { "\"$it\"" }
    }

    /** Whether [range] is well formed. Blank means "no range", which is well formed. */
    fun rangeParses(range: String): Boolean = range.isBlank() || bounds(range) != null

    /** Whether [raw] lies inside [range]. Only ever asked about a number. */
    fun rangeContains(range: String, raw: String): Boolean {
        val (min, max) = bounds(range) ?: return true
        val value = raw.toDoubleOrNull() ?: return false
        return value in min..max
    }

    private fun bounds(range: String): Pair<Double, Double>? {
        if (range.isBlank()) return null
        val separator = range.indexOf(SEPARATOR)
        if (separator <= 0) return null
        val min = range.substring(0, separator).trim().toDoubleOrNull() ?: return null
        val max = range.substring(separator + SEPARATOR.length).trim().toDoubleOrNull() ?: return null
        if (min > max) return null
        return min to max
    }

    private fun String.escaped(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}
