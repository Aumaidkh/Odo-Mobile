package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.logging.api.LogFileHandle
import com.hopcape.logging.api.LogUploadResult
import com.hopcape.logging.api.LogUploadTarget
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.core.platform.app.InstallationId
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The Supabase [LogUploadTarget] — the private `app-logs` bucket, plus one `log_uploads`
 * index row per file (docs/LOGGING_PLAN.md §7).
 *
 * Strictly ordered, never the other way: the object PUT happens first, and only a 2xx there
 * triggers the index insert. An insert failure after a successful PUT is recorded and
 * swallowed — never turned into [LogUploadResult.RETRY] — because retrying would just
 * re-upload identical bytes for a row that only failed to record itself (§7.3).
 *
 * [owners] resolves the storage path's owner segment. With nobody signed in
 * (`OwnerId.LOCAL_PLACEHOLDER`) the answer depends on who asked:
 *
 * - **The background pass** ([reference] null) answers [LogUploadResult.RETRY] without
 *   attempting anything — D4: pre-auth files are held, not dropped, and age out on their own
 *   via retention if nobody ever signs in.
 * - **A request the owner made** ([reference] set) uploads anyway, under `anon/`, with the
 *   project's anon key. Odo runs without signing in, so holding those files would mean
 *   somebody reports a problem, gets a reference code in their mail, and support finds
 *   nothing under it — ever. A code that points at nothing is worse than no code.
 */
internal class SupabaseLogUploader(
    private val client: HttpClient,
    private val environment: SupabaseEnvironment,
    private val tokens: AccessTokenProvider,
    private val owners: CurrentOwnerProvider,
    private val appInfo: AppInfo,
    private val installationId: InstallationId,
    private val postgrest: PostgrestClient,
    private val telemetry: SupabaseTelemetry,
) : LogUploadTarget {

    override val name: String = "supabase"

    /**
     * The installation's own id, stable across restarts — the follow-up §7.3 called for.
     *
     * It used to be a per-process random UUID, so every cold start wrote to a new folder and
     * one phone's logs could not be read as one history. `SecureStore` is still the wrong
     * home for it (its own doc says it exists for the Supabase session, and it is
     * deliberately slow); this is a plain pref, because an installation id is not a secret.
     */
    private val deviceId: String get() = installationId.value

    override suspend fun upload(
        file: LogFileHandle,
        bytes: ByteArray,
        reference: String?,
    ): LogUploadResult {
        val ownerId = owners.currentOwnerId()
        val signedOut = ownerId == OwnerId.LOCAL_PLACEHOLDER
        // Nobody asked and nobody is signed in: hold the file rather than filing it under an
        // owner segment that does not exist yet.
        if (signedOut && reference == null) return LogUploadResult.RETRY

        val ownerSegment = if (signedOut) ANON_SEGMENT else ownerId.value

        return telemetry.span(operation = SupabaseTelemetry.UPLOAD, resource = BUCKET) {
            uploadObjectThenIndexRow(ownerSegment, file, bytes, reference)
        }
    }

    private suspend fun uploadObjectThenIndexRow(
        ownerSegment: String,
        file: LogFileHandle,
        bytes: ByteArray,
        reference: String?,
    ): LogUploadResult {
        val path = "$ownerSegment/$deviceId/${file.name}"

        val putStatus = runCatching {
            client.post("${environment.storageUrl}/object/$BUCKET/$path") {
                authorize()
                header(UPSERT_HEADER, "true")
                contentType(ContentType(GZIP_TYPE, GZIP_SUBTYPE))
                setBody(bytes)
            }.status
        }.getOrElse {
            telemetry.failed(SupabaseTelemetry.UPLOAD, BUCKET, it)
            return LogUploadResult.RETRY
        }

        if (!putStatus.isSuccess()) {
            telemetry.rejected(SupabaseTelemetry.UPLOAD, BUCKET, putStatus.value)
            return when (putStatus.value) {
                // Not the file's fault: an expired token or a storage policy that has not been
                // applied yet. Deleting here would destroy the only copy of logs somebody is
                // waiting on, so these are held and retried like any other transient failure.
                in AUTH_ERROR_CODES -> LogUploadResult.RETRY
                in CLIENT_ERROR_RANGE -> LogUploadResult.REJECTED
                else -> LogUploadResult.RETRY
            }
        }

        insertIndexRow(path, file, reference)
        return LogUploadResult.DELIVERED
    }

    /** Best-effort: the object already landed, so a failure here is recorded, not retried. */
    private suspend fun insertIndexRow(path: String, file: LogFileHandle, reference: String?) {
        runCatching {
            postgrest.upsert(
                table = LOG_UPLOADS_TABLE,
                serializer = LogUploadRow.serializer(),
                rows = listOf(LogUploadRow.from(path, deviceId, appInfo.versionName, file, reference)),
                returnRows = false,
            )
        }.onFailure { telemetry.failed(SupabaseTelemetry.UPSERT, LOG_UPLOADS_TABLE, it) }
    }

    private suspend fun HttpRequestBuilder.authorize() {
        val token = tokens.currentAccessToken() ?: environment.anonKey
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private companion object {
        const val BUCKET = "app-logs"

        /** The owner segment for a file uploaded before anyone has signed in. */
        const val ANON_SEGMENT = "anon"
        const val LOG_UPLOADS_TABLE = "log_uploads"
        const val UPSERT_HEADER = "x-upsert"
        const val GZIP_TYPE = "application"
        const val GZIP_SUBTYPE = "gzip"
        val CLIENT_ERROR_RANGE = 400..499
        val AUTH_ERROR_CODES = listOf(401, 403)
    }
}

/** Matches `log_uploads` column for column (DB_SCHEMA §13, owed alongside this file).
 *  `owner_id` is absent by design — a `BEFORE INSERT` trigger stamps it from `auth.uid()`. */
@Serializable
private data class LogUploadRow(
    @SerialName("device_id") val deviceId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("sealed_at") val sealedAt: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("app_version") val appVersion: String,
    /** Null when the file was recovered as an orphan — its live counters never ran, and NULL
     *  must not be sent (or read) as "zero errors" (docs/LOGGING_PLAN.md §6.4). */
    @SerialName("line_count") val lineCount: Int?,
    @SerialName("warn_count") val warnCount: Int?,
    @SerialName("error_count") val errorCount: Int?,
    @SerialName("had_fatal") val hadFatal: Boolean?,
    /** The code the owner was shown, when this file belongs to a request they made. Null for
     *  the background pass — nobody is looking that upload up. */
    @SerialName("reference") val reference: String?,
) {
    companion object {
        fun from(
            path: String,
            deviceId: String,
            appVersion: String,
            file: LogFileHandle,
            reference: String?,
        ) = LogUploadRow(
            deviceId = deviceId,
            storagePath = path,
            openedAt = Instant.fromEpochMilliseconds(file.openedAtMs).toString(),
            sealedAt = Instant.fromEpochMilliseconds(file.sealedAtMs).toString(),
            sizeBytes = file.sizeBytes,
            appVersion = appVersion,
            lineCount = file.stats?.lineCount,
            warnCount = file.stats?.warnCount,
            errorCount = file.stats?.errorCount,
            hadFatal = file.stats?.hadFatal,
            reference = reference,
        )
    }
}
