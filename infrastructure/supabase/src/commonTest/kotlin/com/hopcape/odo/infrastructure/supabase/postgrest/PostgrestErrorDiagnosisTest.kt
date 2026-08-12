package com.hopcape.odo.infrastructure.supabase.postgrest

import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a rejected request tells the log.
 *
 * A status on its own is not a diagnosis: 400 covers a bad enum value, a malformed uuid and a
 * missing column, and those have nothing in common to fix. The SQLSTATE and the constraint
 * name say which rule was broken. The row that broke it must not appear — PostgREST quotes it,
 * and for Odo that is a plate number or a bill amount.
 */
class PostgrestErrorDiagnosisTest {

    private val duplicatePlate =
        """{"code":"23505","details":"Key (owner_id, registration_number)=(abc, JK03N3078) already exists.",""" +
            """"hint":null,"message":"duplicate key value violates unique constraint \"uq_cars_owner_reg\""}"""

    @Test
    fun aUniqueViolationNamesItsConstraint() = runTest {
        val harness = harnessRejecting(HttpStatusCode.Conflict, duplicatePlate)

        runCatching { harness.postgrest.select("cars", String.serializer()) }

        assertEquals("23505:uq_cars_owner_reg", harness.rejection()[CAUSE])
    }

    @Test
    fun theOffendingRowIsNeverLogged() = runTest {
        val harness = harnessRejecting(HttpStatusCode.Conflict, duplicatePlate)

        runCatching { harness.postgrest.select("cars", String.serializer()) }

        // The plate is in the body PostgREST sent. It must not survive into a log field.
        val logged = harness.logs.joinToString { (event, fields) -> "$event $fields" }
        assertFalse(logged.contains("JK03N3078"), logged)
        assertFalse(logged.contains("Key ("), logged)
    }

    @Test
    fun aBadEnumValueIsToldApartFromEverythingElse() = runTest {
        val harness = harnessRejecting(
            HttpStatusCode.BadRequest,
            """{"code":"22P02","details":null,"hint":null,""" +
                """"message":"invalid input value for enum fuel_type: \"PETROL\""}""",
        )

        runCatching { harness.postgrest.select("cars", String.serializer()) }

        // No constraint is named, so the SQLSTATE stands alone — still enough to tell this
        // from a duplicate or a policy refusal.
        assertEquals("22P02", harness.rejection()[CAUSE])
    }

    @Test
    fun aRefusedPolicyIsToldApartToo() = runTest {
        val harness = harnessRejecting(
            HttpStatusCode.Forbidden,
            """{"code":"42501","details":null,"hint":null,""" +
                """"message":"new row violates row-level security policy for table \"cars\""}""",
        )

        runCatching { harness.postgrest.select("cars", String.serializer()) }

        // "for table" is not "constraint", so nothing is picked up beyond the SQLSTATE —
        // which is the one that says the request was well-formed and simply not allowed.
        assertEquals("42501", harness.rejection()[CAUSE])
    }

    @Test
    fun anUnrecognisedBodyIsReportedAsNothingRatherThanGuessedAt() = runTest {
        val harness = harnessRejecting(HttpStatusCode.BadGateway, "<html>gateway timeout</html>")

        runCatching { harness.postgrest.select("cars", String.serializer()) }

        val fields = harness.rejection()
        assertEquals(502, fields[STATUS])
        // Falling back to logging the body is exactly the case where guessing about PII is
        // a bad idea, so there is simply no cause.
        assertFalse(fields.containsKey(CAUSE), fields.toString())
    }

    @Test
    fun aSuccessfulCallLogsNoRejection() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }

        harness.postgrest.select("cars", String.serializer())

        assertTrue(harness.logs.none { (event, _) -> event.endsWith(REJECTED) })
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun harnessRejecting(status: HttpStatusCode, body: String) =
        SupabaseTestHarness { MockResponse(body, status) }

    /** Fields of the one `*.rejected` line, failing loudly if there wasn't exactly one. */
    private fun SupabaseTestHarness.rejection(): Map<String, Any?> {
        val rejections = logs.filter { (event, _) -> event.endsWith(REJECTED) }
        check(rejections.size == 1) { "expected one rejection, got ${rejections.size}: $logs" }
        return rejections.single().second
    }

    private companion object {
        /* Field names SupabaseTelemetry logs under. */
        const val STATUS = "status"
        const val CAUSE = "cause"
        const val REJECTED = ".rejected"
    }
}
