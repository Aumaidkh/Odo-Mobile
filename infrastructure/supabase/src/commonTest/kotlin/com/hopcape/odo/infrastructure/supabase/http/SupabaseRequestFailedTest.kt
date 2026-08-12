package com.hopcape.odo.infrastructure.supabase.http

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which refusals a retry can still get past.
 *
 * Getting this wrong is quiet either way. Calling a permanent refusal transient leaves an
 * entity retrying forever while reading as "still working on it"; calling a transient one
 * permanent makes an outage look like broken data.
 */
class SupabaseRequestFailedTest {

    @Test
    fun aDuplicateOrRejectedRowIsPermanent() {
        // The payload is the problem, so the answer never changes.
        listOf(400, 403, 404, 409, 422).forEach { status ->
            assertTrue(failed(status).isPermanent, "HTTP $status should be permanent")
        }
    }

    @Test
    fun anExpiredTokenIsNotPermanent() {
        // The next run refreshes the token before it pushes, so this one really does resolve
        // on its own — treating it as permanent would strand the outbox after every expiry.
        assertFalse(failed(401).isPermanent)
    }

    @Test
    fun beingTooEarlyOrTooFastIsNotPermanent() {
        assertFalse(failed(408).isPermanent)
        assertFalse(failed(429).isPermanent)
    }

    @Test
    fun aServerFaultIsNotPermanent() {
        listOf(500, 502, 503, 504).forEach { status ->
            assertFalse(failed(status).isPermanent, "HTTP $status should be retryable")
        }
    }

    @Test
    fun theMessageCarriesNoResponseBody() {
        // PostgREST echoes the offending row. This exception's message is what ends up in
        // logs and crash reports, so it may only ever name the request.
        val message = failed(409).message.orEmpty()

        assertTrue(message.contains("409"), message)
        assertTrue(message.contains("cars"), message)
        assertTrue(message.contains("upsert"), message)
    }

    private fun failed(status: Int) =
        SupabaseRequestFailed(operation = "upsert", resource = "cars", status = status)
}
