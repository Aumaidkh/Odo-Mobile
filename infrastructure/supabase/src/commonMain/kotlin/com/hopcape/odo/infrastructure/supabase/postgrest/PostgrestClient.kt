package com.hopcape.odo.infrastructure.supabase.postgrest

import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.infrastructure.supabase.http.SupabaseJson
import com.hopcape.odo.infrastructure.supabase.http.SupabaseRequestFailed
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * PostgREST — the REST interface Supabase puts in front of Postgres. Selects, upserts and RPC
 * calls, and nothing else, because that is all Odo's remote ports need.
 *
 * Serializers are passed in rather than reified. That keeps the class free of `inline` (which
 * cannot touch its private members) and, more usefully, means the response body is read as
 * text and its status checked *before* anything tries to parse it — a PostgREST error is
 * valid JSON of a completely different shape, and letting a decoder meet it produces a
 * confusing parse error instead of the HTTP status that says what actually went wrong.
 *
 * Nothing here knows what a service log is. The adapters own the table names and the payload
 * shapes; this owns the protocol.
 */
internal class PostgrestClient(
    private val client: HttpClient,
    private val environment: SupabaseEnvironment,
    private val tokens: AccessTokenProvider,
    private val telemetry: SupabaseTelemetry,
) {

    /**
     * `GET /rest/v1/{table}` with [filters] as PostgREST query parameters.
     *
     * [filters] values carry the operator, PostgREST-style — `"eq.$carId"`, `"gt.$cursor"`,
     * `"is.null"` — because the operator is part of the filter, not a separate concept.
     */
    suspend fun <T> select(
        table: String,
        serializer: KSerializer<T>,
        filters: Map<String, String> = emptyMap(),
        columns: String = ALL_COLUMNS,
        order: String? = null,
    ): List<T> = call(operation = SupabaseTelemetry.SELECT, resource = table) {
        val response = client.get("${environment.restUrl}/$table") {
            authorize()
            url.parameters.append(SELECT_PARAM, columns)
            filters.forEach { (column, filter) -> url.parameters.append(column, filter) }
            order?.let { url.parameters.append(ORDER_PARAM, it) }
        }
        val body = response.readOrThrow(SupabaseTelemetry.SELECT, table)
        SupabaseJson.decodeFromString(ListSerializer(serializer), body)
            .also { telemetry.rows(SupabaseTelemetry.SELECT, table, it.size) }
    }

    /**
     * `POST /rest/v1/{table}` with `Prefer: resolution=merge-duplicates`, i.e. an upsert on the
     * primary key, answering with the rows the server actually stored.
     *
     * `return=representation` is what makes the push useful rather than merely successful: the
     * returned rows carry the server's `updated_at`, which is what a local row's
     * `remote_version` is set from before it goes SYNCED (SYNC_DESIGN §6).
     *
     * An empty [rows] is answered without a request. PostgREST accepts an empty array, but a
     * round trip to be told nothing happened is a round trip on a metered connection.
     */
    suspend fun <T> upsert(
        table: String,
        serializer: KSerializer<T>,
        rows: List<T>,
        returnRows: Boolean = true,
    ): List<T> {
        if (rows.isEmpty()) return emptyList()
        return call(operation = SupabaseTelemetry.UPSERT, resource = table) {
            val prefer = if (returnRows) "$MERGE_DUPLICATES,$RETURN_REPRESENTATION" else "$MERGE_DUPLICATES,$RETURN_MINIMAL"
            val response = client.post("${environment.restUrl}/$table") {
                authorize()
                contentType(ContentType.Application.Json)
                header(PREFER_HEADER, prefer)
                setBody(SupabaseJson.encodeToString(ListSerializer(serializer), rows))
            }
            val body = response.readOrThrow(SupabaseTelemetry.UPSERT, table)
            // A caller that ignores the stored rows gets an empty body to parse, which is
            // both cheaper and one less thing that can fail on a payload nobody reads.
            if (!returnRows) {
                telemetry.rows(SupabaseTelemetry.UPSERT, table, rows.size)
                return@call emptyList()
            }
            SupabaseJson.decodeFromString(ListSerializer(serializer), body)
                .also { telemetry.rows(SupabaseTelemetry.UPSERT, table, it.size) }
        }
    }

    /**
     * `PATCH /rest/v1/{table}` — update the columns in [patch] on every row matching
     * [filters], without reading anything back.
     *
     * For writes that assert a rule across rows this device does not hold copies of, e.g.
     * clearing `is_primary` on whatever other car the server thinks is primary. A filter is
     * required: PostgREST would otherwise apply the patch to every row the policy lets this
     * user touch.
     */
    suspend fun update(
        table: String,
        filters: Map<String, String>,
        patch: JsonObject,
    ) {
        require(filters.isNotEmpty()) { "an unfiltered PATCH would update every visible row" }
        call(operation = SupabaseTelemetry.UPDATE, resource = table) {
            val response = client.patch("${environment.restUrl}/$table") {
                authorize()
                contentType(ContentType.Application.Json)
                header(PREFER_HEADER, RETURN_MINIMAL)
                filters.forEach { (column, filter) -> url.parameters.append(column, filter) }
                setBody(SupabaseJson.encodeToString(JsonElement.serializer(), patch))
            }
            response.readOrThrow(SupabaseTelemetry.UPDATE, table)
        }
    }

    /**
     * `POST /rest/v1/rpc/{function}` — a database function call.
     *
     * The controlled escape hatch for data the client may not read directly. The fairness pool
     * is de-identified and has no client-readable policy at all (DB_SCHEMA §12), so an
     * aggregate reaches the app only through a `SECURITY DEFINER` function called this way.
     */
    suspend fun <T> rpc(
        function: String,
        params: JsonObject,
        serializer: KSerializer<T>,
    ): T = call(operation = SupabaseTelemetry.RPC, resource = function) {
        val response = client.post("${environment.restUrl}/rpc/$function") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(SupabaseJson.encodeToString(JsonElement.serializer(), params))
        }
        val body = response.readOrThrow(SupabaseTelemetry.RPC, function)
        SupabaseJson.decodeFromString(serializer, body)
    }

    /**
     * Span [block], and make sure a request that never got an answer is reported.
     *
     * [readOrThrow] already reports a non-2xx, with the status code a dashboard can act on.
     * Everything else — a timeout, a dropped connection, a DNS failure — gets no further than
     * the socket and would otherwise leave nothing behind but a closed span. For an
     * offline-first app that is the failure worth seeing: sync goes quiet while the app looks
     * perfectly healthy.
     *
     * The throw is always rethrown, so this cannot change what a caller does. Cancellation is
     * passed straight through: a sync pass the app called off is not a fault.
     */
    private suspend fun <T> call(operation: String, resource: String, block: suspend () -> T): T =
        telemetry.span(operation = operation, resource = resource) {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (throwable !is SupabaseRequestFailed) telemetry.failed(operation, resource, throwable)
                throw throwable
            }
        }

    /**
     * Read the body, or throw with the status if the server refused.
     *
     * **The error body is never logged as-is.** PostgREST quotes the offending row, which
     * for Odo means a bill amount or a plate number. Only [diagnosis] goes to the log: the
     * SQLSTATE and the constraint name, which say what rule was broken without saying what
     * value broke it.
     */
    private suspend fun HttpResponse.readOrThrow(operation: String, resource: String): String {
        if (!status.isSuccess()) {
            telemetry.rejected(operation, resource, status.value, bodyAsText().diagnosis())
            throw SupabaseRequestFailed(operation, resource, status.value)
        }
        return bodyAsText()
    }

    /**
     * The safe-to-log part of a PostgREST error: `<sqlstate>` plus the constraint it names.
     *
     * Null when the body is not the shape expected, rather than falling back to logging it —
     * an unrecognised body is exactly the case where guessing about PII is a bad idea.
     */
    private fun String.diagnosis(): String? {
        val code = CODE.find(this)?.groupValues?.get(1) ?: return null
        val constraint = CONSTRAINT.find(this)?.groupValues?.get(1)
        return if (constraint == null) code else "$code:$constraint"
    }

    /** Adds the caller's bearer token. Falls back to the anon key, which is Supabase's default. */
    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        val token = tokens.currentAccessToken() ?: environment.anonKey
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private companion object {
        /** PostgREST reports the SQLSTATE as `code`. */
        val CODE = Regex(""""code"\s*:\s*"([^"]{1,16})"""")

        /**
         * The constraint named by a Postgres violation message. Bounded and quoted, so it
         * cannot pick up a free-text row value.
         */
        val CONSTRAINT = Regex("""constraint\s+\\?"([a-zA-Z0-9_]{1,63})\\?"""")

        const val ALL_COLUMNS = "*"
        const val SELECT_PARAM = "select"
        const val ORDER_PARAM = "order"
        const val PREFER_HEADER = "Prefer"
        const val MERGE_DUPLICATES = "resolution=merge-duplicates"
        const val RETURN_REPRESENTATION = "return=representation"
        const val RETURN_MINIMAL = "return=minimal"
    }
}
