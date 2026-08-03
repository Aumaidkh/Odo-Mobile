package com.hopcape.odo.infrastructure.supabase.http

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
    val status: Int,
) : RuntimeException("supabase $operation on '$resource' failed with HTTP $status")
