package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.data.remote.RemoteStoragePath
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.platform.file.PlatformFileStore

/**
 * Gets a stored file onto *this* device, fetching it from the bucket when it is not here yet.
 *
 * The counterpart to [BlobUploader]. Rows sync and files do not: a second phone signed into
 * the same account pulls the service log and the vault entry, but the bytes behind them stay
 * where they were uploaded. Without this, every document on a new device is a row pointing at
 * a file that is not there.
 *
 * The fetched copy is kept, under the same key the app would have used had the file been
 * added on this device. That is what makes the second open — and every offline one after it —
 * free, which matters because the moment an owner needs their insurance paper is rarely a
 * moment they have signal.
 */
class BlobDownloader(
    private val files: PlatformFileStore,
    private val storage: RemoteFileStorage,
    private val telemetry: DataTelemetry,
) {

    /**
     * Answer with a key that opens on this device, or null if the file cannot be had.
     *
     * [storedPath] is whatever the row carries, which means one of two things depending on
     * where the row came from: a key into this device's storage if the file was added here,
     * or a path inside [bucket] if the row arrived from the server. Both are handled, and the
     * cheap case — the file is already here — costs one `exists` check and no network.
     *
     * A fetched copy goes where [RemoteStoragePath.localCacheKeyFor] says, which is derived
     * from the object's own path — so the caller passes what the row holds and nothing else.
     */
    suspend fun localCopyOf(bucket: RemoteBucket, storedPath: String?): String? {
        if (storedPath.isNullOrBlank()) return null
        // Added on this device: the row holds a key into app storage and the bytes are here.
        if (files.exists(storedPath)) return storedPath

        val localKey = RemoteStoragePath.localCacheKeyFor(bucket, storedPath) ?: return null
        // Fetched on an earlier visit.
        if (files.exists(localKey)) return localKey

        val bytes = storage.download(bucket, storedPath)
            .onLeft { telemetry.failed(DataTelemetry.SYNC, OP_DOWNLOAD, it, bucket.name) }
            .getOrNull()
            ?: return null

        // An object that is there but empty is not a file. Writing it would cache a blank in
        // place of the document and make every later open fail without going back to look.
        if (bytes.isEmpty()) {
            telemetry.missing(DataTelemetry.SYNC, OP_EMPTY, bucket.name)
            return null
        }

        return files.write(localKey, bytes)
            .onLeft { telemetry.failed(DataTelemetry.SYNC, OP_WRITE, it, bucket.name) }
            .getOrNull()
    }

    private companion object {
        const val OP_DOWNLOAD = "blob.download"
        const val OP_EMPTY = "blob.empty"
        const val OP_WRITE = "blob.write"
    }
}
