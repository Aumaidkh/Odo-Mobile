package com.hopcape.odo.web.core.infrastructure.supabase

/**
 * Percent-encodes a value going into a PostgREST query string.
 *
 * A slug is url-safe by construction, but a search term — or a make somebody typed
 * into the catalog admin — is whatever they typed, including the `&` that would
 * otherwise end the filter and start a new parameter.
 *
 * Hand-rolled rather than `encodeURIComponent`, because that is a browser function
 * and this is common code. Unreserved characters are the RFC 3986 set; everything
 * else goes out as its UTF-8 bytes.
 */
fun String.encoded(): String = buildString {
    this@encoded.encodeToByteArray().forEach { byte ->
        val character = byte.toInt().toChar()
        if (byte >= 0 && (character.isLetterOrDigit() || character in "-_.~")) {
            append(character)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

/** Escapes a value going inside a hand-built JSON string literal. */
fun String.jsonEscaped(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

private const val HEX = "0123456789ABCDEF"
