package com.hopcape.logging.api

/**
 * The one place that knows a log file's three related names, so
 * [com.hopcape.logging.internal.sinks.FileSink] and every [LogFileStore] implementation
 * (this module's in-memory one, and `:core:platform`'s on-disk one) agree on them without
 * sharing code.
 *
 * A session's files share a stem — the UTC open time, colons stripped so it is a legal
 * filename everywhere: `2026-08-08T14-32-05Z`. `.log.active` is the file a writer owns
 * right now; `.log.gz` is the same file after an atomic rename to gzip on seal; `.meta`
 * is the small sidecar holding that file's [LogFileStats].
 *
 * Deliberately public: `LogFileStore` is a port a different Gradle module implements
 * (`:core:platform`'s real disk store), and that implementation needs the same naming
 * rules this module's in-memory one uses.
 *
 * The stem has one-second resolution, so two files opened within the same UTC second
 * collide onto the same name. Not a real risk given how rotation actually fires — size
 * (2 MB), UTC midnight, and once per process — but worth knowing if a test drives the
 * clock manually: keep successive opens at least a second apart.
 */
@StableLoggerApi
object LogFileNaming {
    const val ACTIVE_SUFFIX: String = ".log.active"
    const val SEALED_SUFFIX: String = ".log.gz"
    const val META_SUFFIX: String = ".meta"

    /** The UTC `<DATE_TIME>` stem for a file opened at [openedAtMs]. */
    fun stemFor(openedAtMs: Long): String {
        val totalSeconds = floorDiv(openedAtMs, MILLIS_PER_SECOND)
        val days = floorDiv(totalSeconds, SECONDS_PER_DAY)
        val secondOfDay = floorMod(totalSeconds, SECONDS_PER_DAY)
        val (year, month, day) = civilDateFromEpochDay(days)
        val hour = secondOfDay / 3600
        val minute = (secondOfDay % 3600) / 60
        val second = secondOfDay % 60
        return buildString {
            append(year.toString().padStart(4, '0')); append('-')
            append(month.toString().padStart(2, '0')); append('-')
            append(day.toString().padStart(2, '0')); append('T')
            append(hour.toString().padStart(2, '0')); append('-')
            append(minute.toString().padStart(2, '0')); append('-')
            append(second.toString().padStart(2, '0')); append('Z')
        }
    }

    /**
     * The inverse of [stemFor]: recovers the `openedAtMs` a name (active, sealed, or a bare
     * stem) was built from. Needed by an on-disk store — `:core:platform`'s — to answer
     * `LogFileHandle.openedAtMs` for files it didn't seal itself: a `.log.gz` from a previous
     * app version, or a `.log.active` orphan left by a process that died before sealing it.
     * Returns `null` for anything that isn't a well-formed stem rather than throwing — a
     * stray or foreign file in the log directory must not crash the store.
     */
    fun parseOpenedAtMs(name: String): Long? {
        val stem = stemOf(name)
        if (stem.length != STEM_LENGTH) return null
        val year = stem.substring(0, 4).toIntOrNull() ?: return null
        val month = stem.substring(5, 7).toIntOrNull() ?: return null
        val day = stem.substring(8, 10).toIntOrNull() ?: return null
        val hour = stem.substring(11, 13).toIntOrNull() ?: return null
        val minute = stem.substring(14, 16).toIntOrNull() ?: return null
        val second = stem.substring(17, 19).toIntOrNull() ?: return null
        if (stem[4] != '-' || stem[7] != '-' || stem[10] != 'T' ||
            stem[13] != '-' || stem[16] != '-' || stem[19] != 'Z'
        ) return null

        val epochDay = daysFromCivil(year, month, day)
        val secondOfDay = hour * 3600L + minute * 60L + second
        return (epochDay * SECONDS_PER_DAY + secondOfDay) * MILLIS_PER_SECOND
    }

    /** The active file name for a session opened at [openedAtMs]. */
    fun activeFileName(openedAtMs: Long): String = stemFor(openedAtMs) + ACTIVE_SUFFIX

    /** The sealed name for the file currently named [activeFileName] — same stem, new suffix. */
    fun sealedFileName(activeFileName: String): String = stemOf(activeFileName) + SEALED_SUFFIX

    /** The sidecar name for the file currently named [sealedFileName]. */
    fun metaFileName(sealedFileName: String): String = sealedFileName + META_SUFFIX

    fun isActive(name: String): Boolean = name.endsWith(ACTIVE_SUFFIX)

    fun isSealed(name: String): Boolean = name.endsWith(SEALED_SUFFIX)

    private fun stemOf(name: String): String =
        name.removeSuffix(ACTIVE_SUFFIX).removeSuffix(SEALED_SUFFIX)

    /**
     * Epoch-day → proleptic Gregorian (year, month, day), UTC. Hand-rolled rather than
     * `kotlinx.datetime.Instant`/`Clock` — those break the iOS native link on this project's
     * Kotlin version (see `LogEvent.Builder`, which uses `kotlin.time.Clock` for the same
     * reason); this needs only integer arithmetic, so there is nothing to gain by depending
     * on either. Algorithm: Howard Hinnant's `civil_from_days`.
     */
    private fun civilDateFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719468
        val era = floorDiv(z, 146097)
        val dayOfEra = z - era * 146097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
        val year = yearOfEra + era * 400
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthPrime = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
        val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
        val civilYear = if (month <= 2) year + 1 else year
        return Triple(civilYear.toInt(), month.toInt(), day.toInt())
    }

    /** The inverse of [civilDateFromEpochDay] — same algorithm family, `days_from_civil`. */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = floorDiv(y, 400L)
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097 + dayOfEra - 719468
    }

    private fun floorDiv(x: Long, y: Long): Long {
        val q = x / y
        return if (x % y != 0L && (x < 0) != (y < 0)) q - 1 else q
    }

    private fun floorMod(x: Long, y: Long): Long = x - floorDiv(x, y) * y

    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_DAY = 86_400L
    private const val STEM_LENGTH = 20 // "YYYY-MM-DDTHH-MM-SSZ"
}
