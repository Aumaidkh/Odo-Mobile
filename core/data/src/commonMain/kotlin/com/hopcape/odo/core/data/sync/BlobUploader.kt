package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.data.remote.RemoteStoragePath
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.platform.file.PlatformFileStore

/**
 * Gets a row's file onto the server **before** the row that names it.
 *
 * Order is the whole design (SYNC_DESIGN §8). A row pushed first would point at a path that
 * does not exist yet, and if the upload then failed the server would hold a document whose
 * file is missing — which in the Resale Passport is a "Verified" badge over nothing. The
 * Passport is the trust product, so the failure mode has to be "not synced yet", never
 * "synced and broken".
 *
 * Uploads are idempotent: the object path is derived from the row's id, and the storage
 * adapter upserts, so a retry overwrites the same object rather than making a second one.
 *
 * A file that has already been uploaded is not re-read from disk. There is no flag for
 * that — the check is whether the local key still resolves to bytes, and a row whose file
 * the device no longer has is left alone rather than being blanked on the server.
 */
class BlobUploader(
    private val files: PlatformFileStore,
    private val storage: RemoteFileStorage,
    private val telemetry: DataTelemetry,
) {

    /**
     * Upload the file behind [localKey] and answer with the object path the row should
     * carry, or null when there is nothing to upload or the upload failed.
     *
     * Null means "leave the row's path as it was". The caller keeps the row `PENDING`, so
     * the next pass tries again — which is exactly what should happen when a phone is on a
     * train.
     */
    suspend fun upload(
        bucket: RemoteBucket,
        localKey: String?,
        ownerId: String,
        carId: String,
        recordId: String,
        contentType: String,
    ): String? {
        if (localKey.isNullOrBlank()) return null

        val bytes = files.bytes(localKey).getOrNull()
        if (bytes == null) {
            // The row will retry forever and never succeed, so this is the one signal that
            // a document exists whose file the device has lost. Never the key itself — it
            // starts with the owner's id.
            telemetry.missing(DataTelemetry.SYNC, OP_READ, bucket.name)
            return null
        }
        // Zero bytes is a file that is not really there. Uploading it would replace a good
        // object on the server with an empty one.
        if (bytes.isEmpty()) {
            telemetry.missing(DataTelemetry.SYNC, OP_EMPTY, bucket.name)
            return null
        }

        val path = RemoteStoragePath.of(
            ownerId = ownerId,
            carId = carId,
            recordId = recordId,
            extension = localKey.substringAfterLast('.', DEFAULT_EXTENSION),
        )

        return storage.upload(bucket, path, bytes, contentType)
            .onLeft { telemetry.failed(DataTelemetry.SYNC, OP_UPLOAD, it, bucket.name) }
            .getOrNull()
    }

    private companion object {
        const val DEFAULT_EXTENSION = "bin"
        const val OP_READ = "blob.read"
        const val OP_EMPTY = "blob.empty"
        const val OP_UPLOAD = "blob.upload"
    }
}

/**
 * The MIME type for a stored file, guessed from its extension.
 *
 * A guess is enough: Supabase Storage records whatever it is told and serves it back, so the
 * cost of being wrong is a browser preview that downloads instead of rendering — not a
 * corrupt file. Odo only ever stores bill photos and vault papers, so the list is short and
 * anything unrecognised falls back to a type that means "bytes".
 */
fun contentTypeOf(key: String?): String = when (key?.substringAfterLast('.', "")?.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}
