package com.hopcape.odo.feature.profile.presentation.logs

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** `HH:mm:ss.SSS` in the device's own zone — Logcat's own timestamp shape, millisecond
 *  precision being the point: two lines a Logcat user needs to tell apart are often in the
 *  same second. */
internal fun formatLogTime(timestampMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(zone)
    return buildString {
        append(dt.hour.toString().padStart(2, '0')); append(':')
        append(dt.minute.toString().padStart(2, '0')); append(':')
        append(dt.second.toString().padStart(2, '0')); append('.')
        append((dt.nanosecond / 1_000_000).toString().padStart(3, '0'))
    }
}
