package com.hopcape.odo.infrastructure.supabase.http

import com.hopcape.odo.core.data.sync.SyncRejection

/**
 * A Supabase call the server answered with a non-2xx status.
 *
 * The message carries the operation, the resource and the status code — never the response
 * body. PostgREST echoes the offending row back in its error payload, so a body in a log line
 * is a bill amount or a registration number in a log line.
 *
 * Thrown rather than returned because the row-syncing ports (`ServiceLogRemoteDataSource` and
 * friends) have no error type in their signatures: the sync engine treats a throw as "leave
 * the rows PENDING and retry", which is exactly the right answer for a rejected push.
 */
internal class SupabaseRequestFailed(
    val operation: String,
    val resource: String,
    override val status: Int,
) : RuntimeException("supabase $operation on '$resource' failed with HTTP $status"), SyncRejection {

    /**
     * Whether this will still be refused however many times it is sent again.
     *
     * A duplicate key, a value the schema rejects or a policy that refuses the row are
     * decisions about the payload: the same request gets the same answer forever, and the
     * scheduler's backoff just walks it out to hours. A 5xx, a timeout or a rate limit are
     * about the moment, and those are exactly what retrying is for.
     *
     * `401` counts as retryable on purpose — it usually means the access token expired
     * mid-run, and the next run refreshes it first.
     */
    override val isPermanent: Boolean
        get() = status in CLIENT_ERRORS && status !in RETRYABLE_CLIENT_ERRORS

    private companion object {
        val CLIENT_ERRORS = 400..499

        /** Client errors that a later attempt can still get past. */
        val RETRYABLE_CLIENT_ERRORS = setOf(
            401, // token expired; refreshed before the next run
            408, // the server gave up waiting, not a bad request
            429, // rate limited
        )
    }
}
