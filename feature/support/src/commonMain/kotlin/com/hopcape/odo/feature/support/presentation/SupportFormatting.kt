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

private const val KILOBYTE = 1024L
private const val MEGABYTE = KILOBYTE * KILOBYTE
