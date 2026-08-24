package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.remote.RemoteStoragePath
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import com.hopcape.odo.infrastructure.supabase.bodyBytes
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The storage adapter against a scripted Supabase Storage. */
class SupabaseRemoteFileStorageTest {

    @Test
    fun `an upload puts the bytes at the bucket path and overwrites`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"Key":"documents/owner-1/car-1/doc-1.pdf"}""") }
        val bytes = byteArrayOf(1, 2, 3)

        val result = storage(harness).upload(
            bucket = RemoteBucket.DOCUMENTS,
            path = "owner-1/car-1/doc-1.pdf",
            bytes = bytes,
            contentType = "application/pdf",
        )

        assertEquals("owner-1/car-1/doc-1.pdf", result.getOrNull())
        val request = harness.onlyRequest()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/storage/v1/object/documents/owner-1/car-1/doc-1.pdf", request.url.encodedPath)
        // Odo names objects after the row id, so re-uploading is a correction, not a conflict.
        assertEquals("true", request.headers["x-upsert"])
        assertContains(request.bodyBytes().toList(), 2)
    }

    @Test
    fun `a refused upload becomes a PersistenceFailure carrying no path`() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse(
                body = """{"error":"new row violates policy for owner-1/car-1/doc-1.pdf"}""",
                status = HttpStatusCode.Forbidden,
            )
        }

        val result = storage(harness).upload(
            bucket = RemoteBucket.DOCUMENTS,
            path = "owner-1/car-1/doc-1.pdf",
            bytes = byteArrayOf(1),
            contentType = "application/pdf",
        )

        val error = result.leftOrNull()
        assertTrue(error is DomainError.PersistenceFailure)
        // The cause is the exception's type name — never a storage path, which starts with
        // the owner's id.
        assertEquals("SupabaseRequestFailed", error.cause)
    }

    @Test
    fun `a signed URL is made absolute against the storage base`() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""{"signedURL":"/object/sign/documents/owner-1/car-1/doc-1.pdf?token=abc"}""")
        }

        val url = storage(harness).signedUrl(
            bucket = RemoteBucket.DOCUMENTS,
            path = "owner-1/car-1/doc-1.pdf",
        ).getOrNull()

        // The server answers with a path relative to /storage/v1, not something openable.
        assertEquals(
            "https://project.supabase.co/storage/v1/object/sign/documents/owner-1/car-1/doc-1.pdf?token=abc",
            url,
        )
        assertEquals("/storage/v1/object/sign/documents/owner-1/car-1/doc-1.pdf", harness.onlyRequest().url.encodedPath)
    }

    @Test
    fun `removing nothing makes no request`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }
        storage(harness).remove(RemoteBucket.BILL_PHOTOS, emptyList())

        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun `a failed remove is swallowed because the row that owned the file is already gone`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"error":"nope"}""", HttpStatusCode.InternalServerError) }

        // No exception: a leftover object is wasted storage, not a broken feature.
        storage(harness).remove(RemoteBucket.BILL_PHOTOS, listOf("owner-1/car-1/bill-1.jpg"))

        assertTrue(harness.requests.isNotEmpty())
    }

    @Test
    fun `a server error is retried before it is given up on`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"error":"nope"}""", HttpStatusCode.InternalServerError) }

        storage(harness).upload(RemoteBucket.BILL_PHOTOS, "owner-1/car-1/bill-1.jpg", byteArrayOf(1), "image/jpeg")

        // The original attempt plus two retries. A dropped connection mid-sync is the common
        // case on an Indian mobile network, and one failed attempt is not an outage.
        assertEquals(3, harness.requests.size)
    }

    @Test
    fun `a refusal is not retried because permission does not change on a second try`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("""{"error":"denied"}""", HttpStatusCode.Forbidden) }

        storage(harness).upload(RemoteBucket.BILL_PHOTOS, "owner-1/car-1/bill-1.jpg", byteArrayOf(1), "image/jpeg")

        assertEquals(1, harness.requests.size)
    }

    @Test
    fun `a storage path starts with the owner id which is what the RLS policy keys on`() {
        val path = RemoteStoragePath.of(
            ownerId = "owner-1",
            carId = "car-1",
            recordId = "doc-1",
            extension = ".PDF",
        )

        assertEquals("owner-1/car-1/doc-1.pdf", path)
        assertTrue(path.startsWith("owner-1/"))
    }

    private fun storage(harness: SupabaseTestHarness) = SupabaseRemoteFileStorage(
        client = harness.client,
        environment = harness.environment,
        tokens = AccessTokenProvider { null },
        telemetry = harness.telemetry,
    )
}
