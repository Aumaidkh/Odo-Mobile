package com.hopcape.odo.infrastructure.database.diagnostics

import com.hopcape.logging.api.DiagnosticRequests
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * SQLDelight-backed [DiagnosticRequests]. Device-local; the table carries no sync columns.
 *
 * Times are written as ISO-8601 strings, like every other timestamp in this database, so the
 * oldest-first read is a plain string sort and needs no conversion.
 *
 * Delivered requests older than [RETENTION] are dropped whenever one is delivered. Pruning
 * here rather than on a schedule keeps it to the one moment the table is already being
 * written to, and the outbox is small enough that a delete of a handful of rows costs
 * nothing.
 */
internal class SqlDelightDiagnosticRequests(
    private val database: OdoDatabase,
    private val clock: Clock,
) : DiagnosticRequests {

    private val queries get() = database.diagnosticRequestQueries

    override suspend fun open(reference: String, createdAtEpochMs: Long) {
        queries.openRequest(
            reference = reference,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs).toString(),
        )
    }

    override suspend fun oldestOpen(): String? = queries.selectOldestOpen().executeAsOneOrNull()

    override suspend fun markDelivered(reference: String) {
        queries.markDelivered(reference)
        queries.deleteDeliveredBefore((clock.now() - RETENTION).toString())
    }

    override suspend fun markAttemptFailed(reference: String, error: String?) {
        queries.recordFailedAttempt(lastError = error, reference = reference)
    }

    override suspend fun clearAll() {
        queries.deleteAllRequests()
    }

    private companion object {
        /** Long enough that support can still look up a code from an old ticket. */
        val RETENTION = 90.days
    }
}
