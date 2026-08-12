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

class BlobDownloaderTest {

    private val bucket = RemoteBucket.BILL_PHOTOS

    /** What a row carries on the phone the bill was attached on. */
    private val localKey = "bills/car-1/log-1.jpg"

    /** What the same row carries on a second phone, after it pulled it from the server. */
    private val remotePath = "owner-1/car-1/log-1.jpg"

    private val cacheKey = "restored/bill-photos/owner-1/car-1/log-1.jpg"

    @Test
    fun `a file that is already here costs no network`() = runTest {
        val files = FakeFiles(present = setOf(localKey))
        val storage = FakeStorage()

        val result = downloader(files, storage).localCopyOf(bucket, localKey)

        assertEquals(localKey, result)
        assertEquals(0, storage.downloads)
    }

    @Test
    fun `a bucket path is fetched and kept where it can be found again`() = runTest {
        val files = FakeFiles()
        val storage = FakeStorage(content = byteArrayOf(1, 2, 3))

        val result = downloader(files, storage).localCopyOf(bucket, remotePath)

        assertEquals(cacheKey, result)
        assertEquals(1, storage.downloads)
        assertTrue(cacheKey in files.written)
    }

    @Test
    fun `a second open reads the kept copy instead of fetching again`() = runTest {
        val files = FakeFiles(present = setOf(cacheKey))
        val storage = FakeStorage()

        val result = downloader(files, storage).localCopyOf(bucket, remotePath)

        assertEquals(cacheKey, result)
        assertEquals(0, storage.downloads)
    }

    @Test
    fun `a fetch that fails answers with nothing rather than a key that opens on nothing`() = runTest {
        val files = FakeFiles()
        val storage = FakeStorage(content = null)

        assertNull(downloader(files, storage).localCopyOf(bucket, remotePath))
        assertTrue(files.written.isEmpty())
    }

    @Test
    fun `an empty object is not cached, so a later try can still find the real file`() = runTest {
        val files = FakeFiles()
        val storage = FakeStorage(content = ByteArray(0))

        assertNull(downloader(files, storage).localCopyOf(bucket, remotePath))
        assertTrue(files.written.isEmpty())
    }

    @Test
    fun `a row with no file at all asks for nothing`() = runTest {
        val storage = FakeStorage()

        assertNull(downloader(FakeFiles(), storage).localCopyOf(bucket, null))
        assertNull(downloader(FakeFiles(), storage).localCopyOf(bucket, ""))
        assertEquals(0, storage.downloads)
    }

    @Test
    fun `a path that could climb out of app storage is refused`() = runTest {
        val storage = FakeStorage(content = byteArrayOf(1))

        assertNull(downloader(FakeFiles(), storage).localCopyOf(bucket, "owner/../../etc/passwd"))
        assertEquals(0, storage.downloads)
    }

    private fun downloader(files: FakeFiles, storage: FakeStorage) =
        BlobDownloader(files = files, storage = storage, telemetry = silentDataTelemetry())

    private class FakeFiles(private val present: Set<String> = emptySet()) : PlatformFileStore {
        val written = mutableMapOf<String, ByteArray>()

        override suspend fun save(pickedRef: String, directory: String, fileName: String) =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun delete(storageKey: String) = Unit

        override suspend fun exists(storageKey: String) = storageKey in present || storageKey in written

        override suspend fun bytes(storageKey: String): Either<DomainError, ByteArray> =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun write(storageKey: String, bytes: ByteArray): Either<DomainError, String> {
            written[storageKey] = bytes
            return storageKey.right()
        }
    }

    private class FakeStorage(private val content: ByteArray? = null) : RemoteFileStorage {
        var downloads = 0
            private set

        override suspend fun upload(bucket: RemoteBucket, path: String, bytes: ByteArray, contentType: String) =
            path.right()

        override suspend fun download(bucket: RemoteBucket, path: String): Either<DomainError, ByteArray> {
            downloads++
            return content?.right() ?: DomainError.PersistenceFailure("not found").left()
        }

        override suspend fun signedUrl(bucket: RemoteBucket, path: String, ttlSeconds: Long) =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun remove(bucket: RemoteBucket, paths: List<String>) = Unit
    }
}
