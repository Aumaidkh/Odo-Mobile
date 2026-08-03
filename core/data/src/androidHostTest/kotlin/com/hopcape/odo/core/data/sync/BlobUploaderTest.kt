package com.hopcape.odo.core.data.sync

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Getting a row's file onto the server before the row that names it (SYNC_DESIGN §8).
 *
 * Every case here is about refusing to hand back a path that would end up on a row while
 * the object behind it does not exist — that is a "Verified" badge over nothing in the
 * Resale Passport.
 */
class BlobUploaderTest {

    @Test
    fun theObjectPathStartsWithTheOwnerId_whichIsWhatStorageRlsChecks() = runTest {
        val storage = RecordingStorage()
        val path = uploader(bytes = byteArrayOf(1, 2, 3), storage = storage)
            .upload(RemoteBucket.DOCUMENTS, "documents/doc-1.pdf", "owner-1", "car-1", "doc-1", "application/pdf")

        assertEquals("owner-1/car-1/doc-1.pdf", path)
        assertEquals(RemoteBucket.DOCUMENTS, storage.bucket)
        assertEquals(listOf<Byte>(1, 2, 3), storage.uploaded?.toList())
    }

    @Test
    fun aRowWithNoFileUploadsNothing() = runTest {
        val storage = RecordingStorage()
        val path = uploader(bytes = byteArrayOf(1), storage = storage)
            .upload(RemoteBucket.BILL_PHOTOS, null, "owner-1", "car-1", "log-1", "image/jpeg")

        assertNull(path)
        assertNull(storage.uploaded)
    }

    @Test
    fun aFileTheDeviceNoLongerHasIsLeftAlone() = runTest {
        val storage = RecordingStorage()
        // The row keeps whatever path it had rather than being blanked. Losing the local
        // copy is not a reason to tell the server the file is gone.
        val path = uploader(bytes = null, storage = storage)
            .upload(RemoteBucket.DOCUMENTS, "documents/doc-1.pdf", "owner-1", "car-1", "doc-1", "application/pdf")

        assertNull(path)
        assertNull(storage.uploaded)
    }

    @Test
    fun anEmptyFileIsNotUploaded() = runTest {
        val storage = RecordingStorage()
        // Zero bytes would replace a good object on the server with an empty one.
        val path = uploader(bytes = ByteArray(0), storage = storage)
            .upload(RemoteBucket.DOCUMENTS, "documents/doc-1.pdf", "owner-1", "car-1", "doc-1", "application/pdf")

        assertNull(path)
        assertNull(storage.uploaded)
    }

    @Test
    fun aRefusedUploadYieldsNoPath() = runTest {
        val path = uploader(bytes = byteArrayOf(1), storage = RefusingStorage)
            .upload(RemoteBucket.DOCUMENTS, "documents/doc-1.pdf", "owner-1", "car-1", "doc-1", "application/pdf")

        // Null means "leave the row's path as it was", so it stays PENDING and retries.
        assertNull(path)
    }

    @Test
    fun theExtensionFollowsTheLocalFile() = runTest {
        val storage = RecordingStorage()
        uploader(bytes = byteArrayOf(1), storage = storage)
            .upload(RemoteBucket.BILL_PHOTOS, "bills/log-1.jpg", "owner-1", "car-1", "log-1", "image/jpeg")

        assertEquals("owner-1/car-1/log-1.jpg", storage.path)
    }

    @Test
    fun aFileWithNoExtensionStillGetsAPath() = runTest {
        val storage = RecordingStorage()
        uploader(bytes = byteArrayOf(1), storage = storage)
            .upload(RemoteBucket.DOCUMENTS, "documents/doc-1", "owner-1", "car-1", "doc-1", "application/pdf")

        assertTrue(storage.path.orEmpty().startsWith("owner-1/car-1/doc-1."))
    }

    @Test
    fun contentTypeIsGuessedFromTheExtension() {
        assertEquals("image/jpeg", contentTypeOf("bills/log-1.JPG"))
        assertEquals("application/pdf", contentTypeOf("documents/policy.pdf"))
        // Unrecognised falls back to bytes rather than guessing wrong.
        assertEquals("application/octet-stream", contentTypeOf("documents/scan.xyz"))
        assertEquals("application/octet-stream", contentTypeOf(null))
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun uploader(bytes: ByteArray?, storage: RemoteFileStorage) =
        BlobUploader(files = FileStore(bytes), storage = storage, telemetry = silentDataTelemetry())

    private class FileStore(private val bytes: ByteArray?) : PlatformFileStore {
        override suspend fun save(pickedRef: String, directory: String, fileName: String) =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String) = bytes != null
        override suspend fun bytes(storageKey: String): Either<DomainError, ByteArray> =
            bytes?.right() ?: DomainError.PersistenceFailure("gone").left()
    }

    private class RecordingStorage : RemoteFileStorage {
        var uploaded: ByteArray? = null
        var path: String? = null
        var bucket: RemoteBucket? = null

        override suspend fun upload(
            bucket: RemoteBucket,
            path: String,
            bytes: ByteArray,
            contentType: String,
        ): Either<DomainError, String> {
            this.bucket = bucket
            this.path = path
            this.uploaded = bytes
            return path.right()
        }

        override suspend fun download(bucket: RemoteBucket, path: String) =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun signedUrl(bucket: RemoteBucket, path: String, ttlSeconds: Long) =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun remove(bucket: RemoteBucket, paths: List<String>) = Unit
    }

    private object RefusingStorage : RemoteFileStorage {
        override suspend fun upload(bucket: RemoteBucket, path: String, bytes: ByteArray, contentType: String) =
            DomainError.PersistenceFailure("offline").left()

        override suspend fun download(bucket: RemoteBucket, path: String) =
            DomainError.PersistenceFailure("offline").left()

        override suspend fun signedUrl(bucket: RemoteBucket, path: String, ttlSeconds: Long) =
            DomainError.PersistenceFailure("offline").left()

        override suspend fun remove(bucket: RemoteBucket, paths: List<String>) = Unit
    }
}
