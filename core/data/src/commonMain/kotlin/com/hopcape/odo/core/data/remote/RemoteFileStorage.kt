package com.hopcape.odo.core.data.remote

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The server side of the files a record points at — bill photos and vault documents.
 *
 * A port, declared here next to the row-syncing remote data sources because the two are the
 * same job seen from either end: rows go to Postgres, the bytes they name go to a Storage
 * bucket. `:infrastructure:supabase` implements it; the fake below keeps the graph resolvable
 * until a project exists.
 *
 * Bytes rather than a local file path: reading a file off the device is a platform concern
 * (`PlatformFileStore`), and keeping it out of here means the adapter needs nothing from
 * `:core:platform` and can be tested with a byte array.
 */
interface RemoteFileStorage {

    /**
     * Put [bytes] at [path] in [bucket] and answer with the path the object now lives at.
     *
     * Overwrites an existing object at the same path. Odo names objects after the row's id,
     * so a second upload for the same id is a corrected file, not a different one.
     */
    suspend fun upload(
        bucket: RemoteBucket,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ): Either<DomainError, String>

    /** Fetch an object's bytes — used to restore a file that a reinstall left behind. */
    suspend fun download(bucket: RemoteBucket, path: String): Either<DomainError, ByteArray>

    /**
     * A short-lived URL for an object in a private bucket, valid for [ttlSeconds].
     *
     * Every Odo bucket is private, so this is the only way anything outside the app reads an
     * object. [ttlSeconds] is short by default because a signed URL is a bearer token: it
     * works for whoever holds it, so it should expire before it can be passed around.
     */
    suspend fun signedUrl(
        bucket: RemoteBucket,
        path: String,
        ttlSeconds: Long = DEFAULT_SIGNED_URL_TTL_SECONDS,
    ): Either<DomainError, String>

    /**
     * Delete objects. Best effort and deliberately not an `Either`: the caller removes files
     * because the rows that named them are gone, and a leftover object is wasted storage
     * rather than a broken feature.
     */
    suspend fun remove(bucket: RemoteBucket, paths: List<String>)

    companion object {
        /** Five minutes — long enough to open a document, short enough to be useless if leaked. */
        const val DEFAULT_SIGNED_URL_TTL_SECONDS: Long = 300
    }
}

/**
 * The buckets Odo writes to, with the names they have on the server (DB_SCHEMA §7).
 *
 * Only the two the MVP fills are here. `passports` and `avatars` are in the schema but
 * nothing writes them yet, and a bucket constant with no caller is a name that drifts out of
 * date unnoticed.
 */
enum class RemoteBucket(val bucketName: String) {
    BILL_PHOTOS("bill-photos"),
    DOCUMENTS("documents"),
}

/**
 * Builds the object path inside a bucket.
 *
 * **The first segment must be the owner's id.** Storage RLS policies are keyed on it
 * (DB_SCHEMA §7), so a path that starts with anything else is rejected by the server rather
 * than stored somewhere odd. This lives in common code so no caller has to remember that.
 */
object RemoteStoragePath {

    /** `{ownerId}/{carId}/{recordId}.{ext}` — the convention both private buckets use. */
    fun of(ownerId: String, carId: String, recordId: String, extension: String): String =
        "$ownerId/$carId/$recordId.${extension.removePrefix(".").lowercase()}"
}

/**
 * Stand-in until a Supabase project exists: accepts uploads, stores nothing, and has nothing
 * to hand back.
 *
 * [upload] answers with the path it was given, which is honest about what it is — a server
 * that agrees the object is there. [download] and [signedUrl] refuse instead, because both
 * have a real answer they cannot invent, and returning empty bytes or a fake URL would show
 * the owner a blank document rather than a plain failure.
 */
internal class FakeRemoteFileStorage : RemoteFileStorage {

    override suspend fun upload(
        bucket: RemoteBucket,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ): Either<DomainError, String> = path.right()

    override suspend fun download(bucket: RemoteBucket, path: String): Either<DomainError, ByteArray> =
        Either.Left(DomainError.PersistenceFailure(NOT_CONFIGURED))

    override suspend fun signedUrl(
        bucket: RemoteBucket,
        path: String,
        ttlSeconds: Long,
    ): Either<DomainError, String> = Either.Left(DomainError.PersistenceFailure(NOT_CONFIGURED))

    override suspend fun remove(bucket: RemoteBucket, paths: List<String>) = Unit

    private companion object {
        const val NOT_CONFIGURED = "remote file storage is not configured"
    }
}
