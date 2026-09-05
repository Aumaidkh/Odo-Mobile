package com.hopcape.odo.feature.support.presentation

/**
 * "240 KB" — an upload's size, as the owner is asked to consent to it.
 *
 * Whole units and no decimals below a megabyte: the number is on a button being weighed for a
 * second, not read off a spec sheet. Kilobytes are 1024 bytes here, matching what a file
 * manager shows for the same file.
 */
internal fun formatBytes(bytes: Long): String = when {
    bytes < KILOBYTE -> "$bytes B"
    bytes < MEGABYTE -> "${bytes / KILOBYTE} KB"
    else -> {
        val tenths = (bytes * 10 / MEGABYTE)
        "${tenths / 10}.${tenths % 10} MB"
    }
}

/**
 * "r•••@gmail.com" — enough of an address for the owner to recognise, and not enough for a
 * screenshot of this screen to hand it to anyone.
 *
 * The first character and the whole domain survive: those are what tell somebody *which* of
 * their addresses this is. Anything that is not an address is masked whole rather than
 * printed, because the only way that happens is data arriving in a shape nobody expected.
 */
internal fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at < 1 || at == email.lastIndex) return MASK
    return "${email.first()}$MASK${email.substring(at)}"
}

private const val KILOBYTE = 1024L
private const val MEGABYTE = KILOBYTE * KILOBYTE
private const val MASK = "•••"
