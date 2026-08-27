package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.logging.api.LogFileHandle
import com.hopcape.logging.api.LogFileStats
import com.hopcape.logging.api.LogUploadResult
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.InstallationId
import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import com.hopcape.odo.infrastructure.supabase.bodyBytes
import com.hopcape.odo.infrastructure.supabase.bodyText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseLogUploaderTest {

    private val fakeAppInfo = object : AppInfo {
        override val versionName: String = "1.4.0"
        override val versionCode: Long = 1L
    }

    private val fakeInstallationId = object : InstallationId {
        override val value: String = "install-1"
    }

    private fun owners(id: OwnerId) = CurrentOwnerProvider { id }

    private val sealedStats = LogFileStats(lineCount = 12, warnCount = 1, errorCount = 0, hadFatal = false)
    private val file = LogFileHandle(
        name = "2026-08-08T14-32-05Z.log.gz",
        sizeBytes = 512L,
        openedAtMs = 1_754_663_525_000L,
        sealedAtMs = 1_754_663_600_000L,
        stats = sealedStats,
    )

    private fun uploader(
        harness: SupabaseTestHarness,
        ownerId: OwnerId = OwnerId("owner-1"),
    ) = SupabaseLogUploader(
        client = harness.client,
        environment = harness.environment,
        tokens = AccessTokenProvider { null },
        owners = owners(ownerId),
        appInfo = fakeAppInfo,
        installationId = fakeInstallationId,
        postgrest = harness.postgrest,
        telemetry = harness.telemetry,
    )

    @Test
    fun upload_withNoOneSignedIn_andNoRequestBehindIt_isRetried_andMakesNoRequest() = runTest {
        val harness = SupabaseTestHarness { error("no request should be made") }

        val result = uploader(harness, ownerId = OwnerId.LOCAL_PLACEHOLDER).upload(file, byteArrayOf(1))

        assertEquals(LogUploadResult.RETRY, result)
        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun upload_withNoOneSignedIn_butAReferenceBehindIt_uploadsUnderAnon() = runTest {
        val harness = SupabaseTestHarness { request ->
            if (request.url.encodedPath.contains("/storage/")) MockResponse("{}") else MockResponse("[]")
        }

        val result = uploader(harness, ownerId = OwnerId.LOCAL_PLACEHOLDER)
            .upload(file, byteArrayOf(1), reference = "ODO-AB12-CD34")

        // Odo runs without signing in. Holding these would mean somebody reports a problem,
        // is given a code, and support never finds anything under it.
        assertEquals(LogUploadResult.DELIVERED, result)
        assertTrue(harness.requests[0].url.encodedPath.contains("/app-logs/anon/install-1/"))
        assertTrue(harness.requests[1].bodyText().contains("\"reference\":\"ODO-AB12-CD34\""))
    }

    @Test
    fun upload_success_putsTheObjectThenInsertsTheIndexRow() = runTest {
        val harness = SupabaseTestHarness { request ->
            if (request.url.encodedPath.contains("/storage/")) {
                MockResponse("""{"Key":"app-logs/owner-1/some-device/${file.name}"}""")
            } else {
                MockResponse("[]")
            }
        }

        val result = uploader(harness).upload(file, byteArrayOf(1, 2, 3))

        assertEquals(LogUploadResult.DELIVERED, result)
        assertEquals(2, harness.requests.size)

        val putRequest = harness.requests[0]
        assertEquals(HttpMethod.Post, putRequest.method)
        // The device segment is the installation id, so every cold start from one phone
        // lands in the same folder — it used to be a fresh random UUID per process.
        assertEquals("/storage/v1/object/app-logs/owner-1/install-1/${file.name}", putRequest.url.encodedPath)
        assertEquals("true", putRequest.headers["x-upsert"])
        // Content-Type rides on the body descriptor, not the plain headers map, in Ktor's
        // request model — same reason SupabaseRemoteFileStorageTest never asserts on it there.
        assertEquals(ContentType("application", "gzip"), putRequest.body.contentType)
        assertEquals(byteArrayOf(1, 2, 3).toList(), putRequest.bodyBytes().toList())

        val insertRequest = harness.requests[1]
        assertEquals(HttpMethod.Post, insertRequest.method)
        assertEquals("/rest/v1/log_uploads", insertRequest.url.encodedPath)
        assertFalse(insertRequest.bodyText().contains("owner_id"), "owner_id must never be client-sent — the server trigger stamps it")
        assertTrue(insertRequest.bodyText().contains("\"line_count\":12"))
        assertTrue(insertRequest.bodyText().contains("\"warn_count\":1"))
        assertTrue(insertRequest.bodyText().contains("\"had_fatal\":false"))
        assertTrue(insertRequest.bodyText().contains("\"app_version\":\"1.4.0\""))
        assertTrue(insertRequest.bodyText().contains("\"device_id\":\"install-1\""))
        // Nobody asked for this one, so there is nothing to file it under — and the column
        // must still be sent, never omitted.
        assertTrue(insertRequest.bodyText().contains("\"reference\":null"))
    }

    @Test
    fun upload_withAnOrphanFile_sendsExplicitNullsForItsUnknownStats() = runTest {
        val harness = SupabaseTestHarness { request ->
            if (request.url.encodedPath.contains("/storage/")) MockResponse("{}") else MockResponse("[]")
        }
        val orphan = file.copy(stats = null)

        uploader(harness).upload(orphan, byteArrayOf(1))

        val insertRequest = harness.requests[1]
        assertTrue(
            insertRequest.bodyText().contains("\"line_count\":null"),
            "an orphan's stats must be sent as explicit null, never omitted or zeroed",
        )
    }

    @Test
    fun upload_storagePutRejected_returnsRejected_andNeverAttemptsTheIndexInsert() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"error":"bad"}""", HttpStatusCode.BadRequest) }

        val result = uploader(harness).upload(file, byteArrayOf(1))

        assertEquals(LogUploadResult.REJECTED, result)
        assertEquals(1, harness.requests.size, "a 4xx is not retried, and the index row is never attempted")
    }

    @Test
    fun upload_refusedByStorage_isHeldRatherThanDeleted() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"error":"denied"}""", HttpStatusCode.Forbidden) }

        val result = uploader(harness).upload(file, byteArrayOf(1))

        // An expired token or a policy that has not been applied yet is not the file's fault,
        // and REJECTED would delete the only copy of logs somebody is waiting on.
        assertEquals(LogUploadResult.RETRY, result)
    }

    @Test
    fun upload_storagePutFailsRepeatedly_returnsRetry() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"error":"nope"}""", HttpStatusCode.InternalServerError) }

        val result = uploader(harness).upload(file, byteArrayOf(1))

        assertEquals(LogUploadResult.RETRY, result)
        // The original attempt plus Ktor's own retries — same policy as every other Supabase call.
        assertEquals(3, harness.requests.size)
    }

    @Test
    fun upload_objectLandsButTheIndexInsertFails_stillReturnsDelivered() = runTest {
        val harness = SupabaseTestHarness { request ->
            if (request.url.encodedPath.contains("/storage/")) {
                MockResponse("{}")
            } else {
                MockResponse("""{"error":"nope"}""", HttpStatusCode.InternalServerError)
            }
        }

        val result = uploader(harness).upload(file, byteArrayOf(1))

        // Re-uploading identical bytes for a row that only failed to record itself would just
        // duplicate storage (docs/LOGGING_PLAN.md §7.3's ordering note) — never a retry.
        assertEquals(LogUploadResult.DELIVERED, result)
        assertTrue(harness.nonFatals.isNotEmpty(), "the failed insert must still be recorded somewhere")
    }
}
