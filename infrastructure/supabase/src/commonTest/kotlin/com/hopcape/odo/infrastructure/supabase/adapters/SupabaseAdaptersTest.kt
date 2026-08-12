package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.document.DocumentDto
import com.hopcape.odo.core.data.reminder.ReminderDto
import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import com.hopcape.odo.infrastructure.supabase.bodyText
import com.hopcape.odo.infrastructure.supabase.http.SupabaseRequestFailed
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The adapters against a scripted PostgREST. What is actually under test is the wire contract:
 * the URL, the filters, the headers and the payload shape — the things that only fail once a
 * real project is on the other end, and that no amount of unit-testing a double would catch.
 */
class SupabaseAdaptersTest {

    // ─── service log ────────────────────────────────────────────────────────────────

    @Test
    fun `service log delta pull filters on car and cursor`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }
        SupabaseServiceLogRemoteDataSource(harness.postgrest)
            .fetchSince(carId = "car-1", since = Instant.parse("2026-01-01T00:00:00Z"))

        val url = harness.onlyRequest().url
        assertEquals("/rest/v1/service_logs", url.encodedPath)
        assertEquals("eq.car-1", url.parameters["car_id"])
        assertEquals("gt.2026-01-01T00:00:00Z", url.parameters["updated_at"])
        assertEquals("updated_at.asc", url.parameters["order"])
    }

    @Test
    fun `a first-ever pull sends no cursor`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }
        SupabaseServiceLogRemoteDataSource(harness.postgrest).fetchSince(carId = "car-1", since = null)

        assertNull(harness.onlyRequest().url.parameters["updated_at"])
    }

    @Test
    fun `a pull keeps soft-deleted rows so a tombstone can reach the device`() = runTest {
        val harness = SupabaseTestHarness { request ->
            if (request.url.encodedPath.endsWith("service_log_categories")) MockResponse("[]")
            else MockResponse("[${serviceLogJson(deletedAt = "2026-02-01T00:00:00Z")}]")
        }
        val pulled = SupabaseServiceLogRemoteDataSource(harness.postgrest).fetchSince("car-1", null)

        assertEquals(1, pulled.size)
        assertEquals("2026-02-01T00:00:00Z", pulled.single().deletedAt)
        // No `deleted_at=is.null` filter — that would hide exactly the rows sync needs.
        assertNull(harness.requests.first().url.parameters["deleted_at"])
    }

    @Test
    fun `a push is an upsert that asks for the stored rows back`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[${serviceLogJson()}]") }
        SupabaseServiceLogRemoteDataSource(harness.postgrest).push(listOf(serviceLogDto()))

        val request = harness.onlyRequest()
        assertEquals(HttpMethod.Post, request.method)
        val prefer = request.headers["Prefer"].orEmpty()
        assertContains(prefer, "resolution=merge-duplicates")
        assertContains(prefer, "return=representation")
    }

    @Test
    fun `a push sends the fields the table now has, and categories to their own table`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[${serviceLogJson()}]") }
        SupabaseServiceLogRemoteDataSource(harness.postgrest).push(
            listOf(
                serviceLogDto().copy(
                    billPhotoPath = "bills/local.jpg",
                    fairnessSnapshot = """{"verdict":"FAIR"}""",
                    categories = listOf("oil_change", "brakes"),
                ),
            ),
        )

        assertEquals(2, harness.requests.size)
        val parent = harness.requests[0]
        val children = harness.requests[1]

        // The parent goes first. A category row referencing an entry the server has not
        // accepted is a foreign-key error.
        assertEquals("/rest/v1/service_logs", parent.url.encodedPath)
        assertEquals("/rest/v1/service_log_categories", children.url.encodedPath)

        // These two are real columns now, so they must actually be sent.
        val parentBody = parent.bodyText()
        assertContains(parentBody, "bill_photo_path")
        assertContains(parentBody, "fairness_snapshot")
        // ...but `categories` is not a column on service_logs, and sending it would fail the
        // whole upsert with PGRST204.
        assertTrue("\"categories\"" !in parentBody, "categories must not be sent to service_logs")

        val childBody = children.bodyText()
        assertContains(childBody, "oil_change")
        assertContains(childBody, "brakes")
        // owner_id is stamped from the parent entry by trigger; a client value is overwritten.
        assertTrue("owner_id" !in childBody, "owner_id must not be sent for a category row")
    }

    @Test
    fun `an entry with no categories writes only the parent`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[${serviceLogJson()}]") }
        SupabaseServiceLogRemoteDataSource(harness.postgrest).push(listOf(serviceLogDto()))

        assertEquals(1, harness.requests.size)
    }

    @Test
    fun `a pull fetches categories for the page in one extra request`() = runTest {
        val harness = SupabaseTestHarness { request ->
            if (request.url.encodedPath.endsWith("service_log_categories")) {
                MockResponse("""[{"service_log_id":"log-1","category":"oil_change"}]""")
            } else {
                MockResponse("[${serviceLogJson()}]")
            }
        }

        val pulled = SupabaseServiceLogRemoteDataSource(harness.postgrest).fetchSince("car-1", null)

        assertEquals(listOf("oil_change"), pulled.single().categories)
        // One request per page, not one per entry.
        assertEquals(2, harness.requests.size)
        assertContains(harness.requests[1].url.parameters["service_log_id"].orEmpty(), "in.(log-1)")
    }

    @Test
    fun `an empty page skips the categories request`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }
        SupabaseServiceLogRemoteDataSource(harness.postgrest).fetchSince("car-1", null)

        assertEquals(1, harness.requests.size)
    }

    @Test
    fun `demoting other primaries is one scoped PATCH with no read-back`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("") }
        SupabaseCarRemoteDataSource(harness.postgrest)
            .demoteOtherPrimaries(ownerId = "owner-1", keepCarId = "car-1")

        val request = harness.onlyRequest()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/rest/v1/cars", request.url.encodedPath)
        // Scoped to the owner's other live primaries — an unfiltered PATCH would demote
        // every car the policy lets this user touch.
        assertEquals("eq.owner-1", request.url.parameters["owner_id"])
        assertEquals("is.true", request.url.parameters["is_primary"])
        assertEquals("neq.car-1", request.url.parameters["id"])
        assertEquals("is.null", request.url.parameters["deleted_at"])
        assertEquals("""{"is_primary":false}""", request.bodyText())
        assertContains(request.headers["Prefer"].orEmpty(), "return=minimal")
    }

    @Test
    fun `an empty push makes no request at all`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }
        val pushed = SupabaseServiceLogRemoteDataSource(harness.postgrest).push(emptyList())

        assertTrue(pushed.isEmpty())
        assertTrue(harness.requests.isEmpty(), "an empty outbox should not hit the network")
    }

    @Test
    fun `a rejected push throws with the status and never the body`() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse(
                body = """{"message":"row violates row-level security policy for MH01AB1234"}""",
                status = HttpStatusCode.Forbidden,
            )
        }

        val failure = assertFailsWith<SupabaseRequestFailed> {
            SupabaseServiceLogRemoteDataSource(harness.postgrest).push(listOf(serviceLogDto()))
        }

        assertEquals(403, failure.status)
        assertEquals("service_logs", failure.resource)
        // A PostgREST error body quotes the offending row — it must never reach a log line.
        assertTrue("MH01AB1234" !in failure.message.orEmpty())
    }

    @Test
    fun `a request that never gets an answer is recorded as a non-fatal`() = runTest {
        // No status, no body — the socket-level case: a timeout or a dropped connection.
        // Sync going quiet while the app looks healthy is the failure worth seeing.
        val harness = SupabaseTestHarness { throw IllegalStateException("connection reset") }

        assertFailsWith<IllegalStateException> {
            SupabaseServiceLogRemoteDataSource(harness.postgrest).fetchSince("car-1", null)
        }

        assertEquals(1, harness.nonFatals.size)
        assertIs<IllegalStateException>(harness.nonFatals.single())
    }

    @Test
    fun `a rejected request is reported once, not twice`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"message":"nope"}""", HttpStatusCode.Forbidden) }

        assertFailsWith<SupabaseRequestFailed> {
            SupabaseServiceLogRemoteDataSource(harness.postgrest).fetchSince("car-1", null)
        }

        // The status already went out through `rejected`. Recording it again as a non-fatal
        // would put one failure on the dashboard twice.
        assertTrue(harness.nonFatals.isEmpty(), "a non-2xx must not also be recorded as a non-fatal")
    }

    // ─── documents ──────────────────────────────────────────────────────────────────

    @Test
    fun `a document round-trips without an intermediate row type`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[${documentJson()}]") }
        val pushed = SupabaseDocumentRemoteDataSource(harness.postgrest).push(listOf(documentDto()))

        assertEquals("doc-1", pushed.single().id)
        assertEquals("insurance", pushed.single().docType)
        assertEquals("/rest/v1/documents", harness.onlyRequest().url.encodedPath)
    }

    // ─── reminders ──────────────────────────────────────────────────────────────────

    @Test
    fun `a reminder round-trips with its cadence columns`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[${reminderJson()}]") }
        val pushed = SupabaseReminderRemoteDataSource(harness.postgrest).push(listOf(reminderDto()))

        assertEquals("rem-1", pushed.single().id)
        assertEquals("custom", pushed.single().reminderType)
        assertEquals("every_days", pushed.single().repeatKind)
        assertEquals(15L, pushed.single().repeatEveryDays)
        assertEquals("/rest/v1/reminders", harness.onlyRequest().url.encodedPath)
    }

    @Test
    fun `a reminder pull filters on car and cursor`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }
        SupabaseReminderRemoteDataSource(harness.postgrest)
            .fetchSince(carId = "car-1", since = Instant.parse("2026-08-01T00:00:00Z"))

        val url = harness.onlyRequest().url
        assertEquals("eq.car-1", url.parameters["car_id"])
        assertEquals("gt.2026-08-01T00:00:00Z", url.parameters["updated_at"])
    }

    // ─── fairness ───────────────────────────────────────────────────────────────────

    @Test
    fun `fairness calls the RPC once per category`() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""[{"avg_paise":210000,"sample_size":31,"p25":180000,"p75":245000}]""")
        }
        val estimates = SupabaseFairnessRemoteDataSource(harness.postgrest)
            .estimates(categories = listOf("oil_change", "brakes"), city = "Mumbai")

        assertEquals(2, harness.requests.size)
        assertTrue(harness.requests.all { it.url.encodedPath == "/rest/v1/rpc/get_fairness_estimate" })
        assertEquals(2, estimates.size)
        assertEquals(210000L, estimates.first().cityAveragePaise)
        assertEquals(31, estimates.first().sampleSize)
        assertEquals(180000L, estimates.first().p25Paise)
    }

    @Test
    fun `a category the pool has nothing for is dropped, not reported as zero`() = runTest {
        val harness = SupabaseTestHarness {
            // What `RETURNS TABLE` answers when no data points match: one row of nulls.
            MockResponse("""[{"avg_paise":null,"sample_size":0,"p25":null,"p75":null}]""")
        }
        val estimates = SupabaseFairnessRemoteDataSource(harness.postgrest)
            .estimates(categories = listOf("clutch"), city = "Mumbai")

        assertTrue(estimates.isEmpty(), "a benchmark of nothing is not a benchmark of zero")
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────────

    private fun serviceLogDto() = ServiceLogDto(
        id = "log-1",
        carId = "car-1",
        ownerId = "owner-1",
        serviceDate = "2026-01-15",
        odometerKm = 45_000,
        totalAmountPaise = 280_000,
        source = "manual",
        createdAt = "2026-01-15T10:00:00Z",
        updatedAt = "2026-01-15T10:00:00Z",
    )

    private fun serviceLogJson(deletedAt: String? = null) = """
        {"id":"log-1","car_id":"car-1","owner_id":"owner-1","service_date":"2026-01-15",
         "odometer_km":45000,"total_amount_paise":280000,"source":"manual",
         "created_at":"2026-01-15T10:00:00Z","updated_at":"2026-01-15T10:00:00Z",
         "deleted_at":${deletedAt?.let { "\"$it\"" } ?: "null"}}
    """.trimIndent()

    private fun documentDto() = DocumentDto(
        id = "doc-1",
        carId = "car-1",
        ownerId = "owner-1",
        docType = "insurance",
        storagePath = "owner-1/car-1/doc-1.pdf",
        docSource = "uploaded",
        createdAt = "2026-01-15T10:00:00Z",
        updatedAt = "2026-01-15T10:00:00Z",
    )

    private fun documentJson() = """
        {"id":"doc-1","car_id":"car-1","owner_id":"owner-1","doc_type":"insurance",
         "storage_path":"owner-1/car-1/doc-1.pdf","doc_source":"uploaded",
         "created_at":"2026-01-15T10:00:00Z","updated_at":"2026-01-15T10:00:00Z"}
    """.trimIndent()

    private fun reminderDto() = ReminderDto(
        id = "rem-1",
        carId = "car-1",
        ownerId = "owner-1",
        reminderType = "custom",
        dueDate = "2026-08-10",
        status = "scheduled",
        title = "Air pressure check",
        startsOn = "2026-08-10",
        remindAt = "09:00",
        repeatKind = "every_days",
        repeatEveryDays = 15,
        createdAt = "2026-08-06T10:00:00Z",
        updatedAt = "2026-08-06T10:00:00Z",
    )

    private fun reminderJson() = """
        {"id":"rem-1","car_id":"car-1","owner_id":"owner-1","reminder_type":"custom",
         "due_date":"2026-08-10","status":"scheduled","title":"Air pressure check",
         "is_paused":false,"starts_on":"2026-08-10","remind_at":"09:00:00",
         "repeat_kind":"every_days","repeat_every_days":15,
         "created_at":"2026-08-06T10:00:00Z","updated_at":"2026-08-06T10:00:00Z"}
    """.trimIndent()
}
