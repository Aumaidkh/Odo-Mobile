package com.hopcape.odo.web.core.infrastructure.supabase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.core.domain.WebError
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * PostgREST, in the four shapes the blog needs.
 *
 * A smaller sibling of the one in `:infrastructure:supabase` — that module cannot
 * be reused here because it has no Wasm target, and porting it would mean giving
 * it one along with everything it depends on.
 *
 * The two habits worth keeping from it are kept. The body is read as text and the
 * status checked **before** anything parses it: a PostgREST error is valid JSON of
 * an entirely different shape, and letting a decoder meet it produces a confusing
 * parse error instead of the status that says what went wrong. And serializers
 * are passed in rather than reified, so this class stays free of `inline`.
 *
 * Nothing here knows what a post is. The repositories own the table names and the
 * payload shapes; this owns the protocol.
 */
class Postgrest(
    private val client: HttpClient,
    private val baseUrl: String,
    private val anonKey: String,
    /** The signed-in author's access token, or null for anonymous reads. */
    private val accessToken: suspend () -> String?,
) {

    /** `GET /rest/v1/{table}`. [query] carries PostgREST's own filter syntax. */
    suspend fun <T> select(
        table: String,
        serializer: KSerializer<T>,
        query: String = "",
    ): Either<WebError, List<T>> = request {
        client.get("$baseUrl/rest/v1/$table${query.prefixed()}") { headers() }
    }.flatMap { body -> decode(ListSerializer(serializer), body) }

    /**
     * `POST /rest/v1/{table}` with `Prefer: resolution=merge-duplicates`.
     *
     * Upsert rather than insert, because saving a draft is the same call whether
     * or not the row exists — the alternative is the caller tracking which, and
     * being wrong the one time it matters.
     */
    suspend fun <T> upsert(
        table: String,
        body: String,
        serializer: KSerializer<T>,
        onConflict: String = "id",
        /**
         * True to let a duplicate pass silently instead of overwriting.
         *
         * Not a style choice: `merge-duplicates` is an INSERT ... ON CONFLICT DO
         * UPDATE, which needs an UPDATE policy as well as an INSERT one. On a
         * table anonymous readers may write to but never read — a subscriber list
         * — granting UPDATE would let a stranger rewrite a row they cannot see.
         */
        ignoreDuplicates: Boolean = false,
    ): Either<WebError, List<T>> = request {
        client.post("$baseUrl/rest/v1/$table?on_conflict=$onConflict") {
            headers()
            // `return=representation` so the caller gets the row as stored —
            // including the id the database just generated, which is the whole
            // reason a first save differs from the ones after it.
            val resolution = if (ignoreDuplicates) "ignore-duplicates" else "merge-duplicates"
            header("Prefer", "resolution=$resolution,return=representation")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.flatMap { text -> decode(ListSerializer(serializer), text) }

    /**
     * A plain `POST /rest/v1/{table}` — insert, and nothing clever.
     *
     * Separate from [upsert] because sending any `resolution=` preference at all
     * makes PostgREST treat the request as an upsert, and an upsert wants an
     * UPDATE policy even when it is told to ignore duplicates. On a table
     * anonymous readers may write to but never read, granting UPDATE would let a
     * stranger rewrite a row they cannot see — so the request has to stay an
     * ordinary insert.
     *
     * [conflictIsFine] swallows the 409 a unique constraint produces. Subscribing
     * twice is not something to report to the person doing it.
     */
    suspend fun insert(
        table: String,
        body: String,
        conflictIsFine: Boolean = false,
    ): Either<WebError, Unit> {
        val response = runCatching {
            client.post("$baseUrl/rest/v1/$table") {
                headers()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.getOrNull() ?: return WebError.Offline.left()

        if (response.status.isSuccess()) return Unit.right()
        if (conflictIsFine && response.status == HttpStatusCode.Conflict) return Unit.right()

        val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        return when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> WebError.NotSignedIn
            else -> WebError.Unexpected("postgrest ${response.status.value}: ${text.take(200)}")
        }.left()
    }

    /** `PATCH /rest/v1/{table}` — a partial update over whatever [query] matches. */
    suspend fun patch(table: String, query: String, body: String): Either<WebError, Unit> =
        request {
            client.patch("$baseUrl/rest/v1/$table${query.prefixed()}") {
                headers()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.map { }

    suspend fun delete(table: String, query: String): Either<WebError, Unit> =
        request { client.delete("$baseUrl/rest/v1/$table${query.prefixed()}") { headers() } }.map { }

    /** `POST /rest/v1/rpc/{name}`. [body] is the argument object. */
    suspend fun <T> rpc(
        name: String,
        body: String,
        serializer: KSerializer<T>,
    ): Either<WebError, List<T>> = request {
        client.post("$baseUrl/rest/v1/rpc/$name") {
            headers()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.flatMap { text -> decode(ListSerializer(serializer), text) }

    /**
     * An RPC that returns one value rather than a set.
     *
     * A Postgres function declared `returns jsonb` answers with the object itself,
     * not an array wrapping it, so [rpc]'s list decoding fails on the shape. The
     * two cannot be one method: which of them applies is decided by the function's
     * return type, and only the caller knows it.
     *
     * Null is a real answer, not a failure. `my_admin_identity()` returns SQL NULL
     * for a caller who is not staff, and that is the whole point of asking.
     */
    suspend fun <T> rpcOne(
        name: String,
        body: String,
        serializer: KSerializer<T>,
    ): Either<WebError, T?> = request {
        client.post("$baseUrl/rest/v1/rpc/$name") {
            headers()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.flatMap { text ->
        if (text.isBlank() || text == "null") {
            null.right()
        } else {
            runCatching { LENIENT.decodeFromString(serializer, text) }
                .fold({ it.right() }, { WebError.Unexpected("unreadable response: ${it.message}").left() })
        }
    }

    /** An RPC whose return value nobody reads — counting a page view. */
    suspend fun call(name: String, body: String): Either<WebError, Unit> =
        request {
            client.post("$baseUrl/rest/v1/rpc/$name") {
                headers()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.map { }

    /**
     * Both keys go on every request.
     *
     * `apikey` names the project; `Authorization` says who is asking. Signed out
     * they are the same anon key, and PostgREST resolves the request as the `anon`
     * role — which is exactly what the public side wants.
     */
    private suspend fun io.ktor.client.request.HttpRequestBuilder.headers() {
        header("apikey", anonKey)
        header(HttpHeaders.Authorization, "Bearer ${accessToken() ?: anonKey}")
    }

    /**
     * Runs a call and turns everything that can go wrong into a [WebError].
     *
     * A thrown exception means the request never completed — offline, DNS, a
     * blocked origin. A non-2xx means it did, and the status says what happened:
     * 401 and 403 are a session that has run out or a policy that said no, which
     * the CMS draws as "sign in again" rather than as a failure.
     */
    private suspend fun request(block: suspend () -> HttpResponse): Either<WebError, String> {
        val response = runCatching { block() }.getOrNull() ?: return WebError.Offline.left()
        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        if (response.status.isSuccess()) return body.right()
        return when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> WebError.NotSignedIn
            HttpStatusCode.NotFound -> WebError.NotFound
            else -> WebError.Unexpected("postgrest ${response.status.value}: ${body.take(200)}")
        }.left()
    }

    private fun <T> decode(serializer: KSerializer<List<T>>, body: String): Either<WebError, List<T>> =
        runCatching { LENIENT.decodeFromString(serializer, body) }
            .fold({ it.right() }, { WebError.Unexpected("unreadable response: ${it.message}").left() })

    private fun String.prefixed(): String = if (isBlank()) "" else "?$this"

    internal companion object {
        /**
         * Ignores columns we do not model. The database will grow columns this
         * client has never heard of, and a strict parser would turn every read
         * into a failure on the day one appears.
         */
        val LENIENT = Json { ignoreUnknownKeys = true }

        /**
         * Writes nulls, and every default.
         *
         * PostgREST treats an absent key as "leave this column alone", so a field
         * cleared in the CMS would silently keep its old value — the same trap the
         * app's Supabase adapters already carry a note about.
         */
        val PAYLOAD = Json { encodeDefaults = true; explicitNulls = true }
    }
}

/** `flatMap` for the shape used above. Arrow has one; this avoids the import churn. */
private inline fun <A, B> Either<WebError, A>.flatMap(block: (A) -> Either<WebError, B>): Either<WebError, B> =
    fold({ it.left() }, block)
