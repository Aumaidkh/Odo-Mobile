package com.hopcape.odo.infrastructure.supabase.adapters

import arrow.core.Either
import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.remote.RemoteFileStorage
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.infrastructure.supabase.http.SupabaseJson
import com.hopcape.odo.infrastructure.supabase.http.SupabaseRequestFailed
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Supabase Storage — the `bill-photos` and `documents` buckets.
 *
 * Both are private, and their RLS policies key on the **first path segment being the owner's
 * id** (DB_SCHEMA §7). `RemoteStoragePath` builds paths that way, so callers do not have to
 * remember it; a path that starts with anything else is refused by the server.
 *
 * Unlike the row adapters this returns `Either` rather than throwing, because
 * `RemoteFileStorage` has an error type and its callers act on failure directly — a document
 * whose bytes will not upload is something the owner is told about, not something a sync pass
 * quietly retries forever.
 */
internal class SupabaseRemoteFileStorage(
    private val client: HttpClient,
    private val environment: SupabaseEnvironment,
    private val tokens: AccessTokenProvider,
    private val telemetry: SupabaseTelemetry,
) : RemoteFileStorage {

    override suspend fun upload(
        bucket: RemoteBucket,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ): Either<DomainError, String> = attempt(SupabaseTelemetry.UPLOAD, bucket) {
        val response = client.post(objectUrl(bucket, path)) {
            authorize()
            // Odo names objects after the row's id, so a second upload for the same id is a
            // corrected file rather than a different one. Without this the server answers 409.
            header(UPSERT_HEADER, "true")
            contentType(ContentType.parse(contentType))
            setBody(bytes)
        }
        response.throwIfRejected(SupabaseTelemetry.UPLOAD, bucket.bucketName)
        path
    }

    override suspend fun download(
        bucket: RemoteBucket,
        path: String,
    ): Either<DomainError, ByteArray> = attempt(SupabaseTelemetry.DOWNLOAD, bucket) {
        val response = client.get(objectUrl(bucket, path)) { authorize() }
        response.throwIfRejected(SupabaseTelemetry.DOWNLOAD, bucket.bucketName)
        response.bodyAsBytes()
    }

    override suspend fun signedUrl(
        bucket: RemoteBucket,
        path: String,
        ttlSeconds: Long,
    ): Either<DomainError, String> = attempt(SupabaseTelemetry.SIGN, bucket) {
        val response = client.post("${environment.storageUrl}/object/sign/${bucket.bucketName}/$path") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(
                SupabaseJson.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(mapOf(EXPIRES_IN to JsonPrimitive(ttlSeconds))),
                ),
            )
        }
        response.throwIfRejected(SupabaseTelemetry.SIGN, bucket.bucketName)
        val signed = SupabaseJson.decodeFromString(SignedUrlResponse.serializer(), response.bodyAsText())
        // The server answers with a path relative to /storage/v1, not an absolute URL.
        "${environment.storageUrl}${signed.signedUrl.removePrefix(STORAGE_PREFIX)}"
    }

    override suspend fun remove(bucket: RemoteBucket, paths: List<String>) {
        if (paths.isEmpty()) return
        telemetry.span(operation = SupabaseTelemetry.REMOVE, resource = bucket.bucketName) {
            runCatching {
                val response = client.delete("${environment.storageUrl}/object/${bucket.bucketName}") {
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(SupabaseJson.encodeToString(RemoveRequest.serializer(), RemoveRequest(paths)))
                }
                if (!response.status.isSuccess()) {
                    telemetry.rejected(SupabaseTelemetry.REMOVE, bucket.bucketName, response.status.value)
                }
            }.onFailure { telemetry.failed(SupabaseTelemetry.REMOVE, bucket.bucketName, it) }
        }
    }

    /**
     * Runs [block] inside a span and turns anything it throws into a `PersistenceFailure`.
     *
     * The cause is the exception's *type name*, never its message: a Ktor or PostgREST message
     * can quote the request, and this port carries storage paths that start with the owner's id.
     */
    private suspend fun <T> attempt(
        operation: String,
        bucket: RemoteBucket,
        block: suspend () -> T,
    ): Either<DomainError, T> = telemetry.span(operation = operation, resource = bucket.bucketName) {
        Either.catch { block() }
            .onLeft { telemetry.failed(operation, bucket.bucketName, it) }
            .mapLeft { DomainError.PersistenceFailure(it::class.simpleName) }
    }

    private fun objectUrl(bucket: RemoteBucket, path: String) =
        "${environment.storageUrl}/object/${bucket.bucketName}/$path"

    private suspend fun HttpResponse.throwIfRejected(operation: String, resource: String) {
        if (!status.isSuccess()) {
            telemetry.rejected(operation, resource, status.value)
            throw SupabaseRequestFailed(operation, resource, status.value)
        }
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        val token = tokens.currentAccessToken() ?: environment.anonKey
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private companion object {
        const val UPSERT_HEADER = "x-upsert"
        const val EXPIRES_IN = "expiresIn"
        const val STORAGE_PREFIX = "/storage/v1"
    }
}

@Serializable
private data class SignedUrlResponse(@SerialName("signedURL") val signedUrl: String)

@Serializable
private data class RemoveRequest(@SerialName("prefixes") val prefixes: List<String>)
