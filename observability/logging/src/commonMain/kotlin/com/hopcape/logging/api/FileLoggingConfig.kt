package com.hopcape.logging.api

/**
 * Configures the durable file sink: where it writes, when it rotates, and how much it is
 * allowed to keep on disk. Passed via [LoggerConfig.fileLogging].
 *
 * [store] is a constructed [LogFileStore], not a path — the same shape as
 * `CrashConfig.destinations` and `AnalyticsConfig.destinations`. This module cannot turn a
 * path into a real store itself: the on-disk implementation lives in `:core:platform`, which
 * depends on `:observability:logging` and not the other way around, so only the platform
 * bootstrap (e.g. Android's `OdoApplication`) can construct one and hand it in.
 */
@StableLoggerApi
data class FileLoggingConfig(
    val store: LogFileStore,
    val maxActiveFileBytes: Long = DEFAULT_MAX_ACTIVE_FILE_BYTES,
    val rotateAtUtcMidnight: Boolean = true,
    val retention: RetentionPolicy = RetentionPolicy(),
) {
    /** Applied after every seal, in this order: age, then total size, then count. */
    @StableLoggerApi
    data class RetentionPolicy(
        val maxAgeDays: Int = DEFAULT_MAX_AGE_DAYS,
        val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        val maxFileCount: Int = DEFAULT_MAX_FILE_COUNT,
    )

    companion object {
        const val DEFAULT_MAX_ACTIVE_FILE_BYTES: Long = 2L * 1024 * 1024
        const val DEFAULT_MAX_AGE_DAYS: Int = 7
        const val DEFAULT_MAX_TOTAL_BYTES: Long = 20L * 1024 * 1024
        const val DEFAULT_MAX_FILE_COUNT: Int = 10
    }
}
