package com.hopcape.logging.internal.file

/**
 * Decides whether the active file should be sealed before the next batch of lines is
 * appended. Evaluated once per [com.hopcape.logging.internal.sinks.FileSink.write] call,
 * against the file currently open — never mid-batch, since splitting one flush across two
 * files buys nothing.
 *
 * Session rotation — "a new file on every cold start" — is not a per-event policy here.
 * [FileSink] starts with no file open on construction (one instance per process), and any
 * `.active` file left on disk by a previous process is sealed once at startup by
 * `LogFileStore.sealOrphans()` before the sink writes anything — so the first line of a new
 * process always lands in a fresh file without this policy needing to know "is this a new
 * session?".
 */
internal fun interface RotationPolicy {
    fun shouldRotate(openedAtMs: Long, activeSizeBytes: Long, nowMs: Long): Boolean

    companion object {
        /** Rolls once the active file reaches [maxBytes]. */
        fun maxSize(maxBytes: Long): RotationPolicy =
            RotationPolicy { _, activeSizeBytes, _ -> activeSizeBytes >= maxBytes }

        /** Rolls the first write that lands on a later UTC calendar day than the file was opened. */
        fun utcMidnight(): RotationPolicy =
            RotationPolicy { openedAtMs, _, nowMs -> epochDayUtc(openedAtMs) != epochDayUtc(nowMs) }

        /** Rolls when any of [policies] would — the composition every real config uses. */
        fun anyOf(vararg policies: RotationPolicy): RotationPolicy =
            RotationPolicy { openedAtMs, activeSizeBytes, nowMs ->
                policies.any { it.shouldRotate(openedAtMs, activeSizeBytes, nowMs) }
            }

        private fun epochDayUtc(epochMs: Long): Long = floorDiv(floorDiv(epochMs, 1_000L), 86_400L)

        private fun floorDiv(x: Long, y: Long): Long {
            val q = x / y
            return if (x % y != 0L && (x < 0) != (y < 0)) q - 1 else q
        }
    }
}
