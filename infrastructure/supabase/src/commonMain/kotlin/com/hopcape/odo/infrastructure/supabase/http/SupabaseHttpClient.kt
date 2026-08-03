package com.hopcape.odo.infrastructure.supabase.http

import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath
import kotlinx.serialization.json.Json

/**
 * The one HTTP client every Supabase call goes through.
 *
 * Deliberately plain Ktor rather than supabase-kt: every supabase-kt release up to 3.2.0
 * compiles against kotlinx-datetime 0.6.2, and 0.7.0 removed the `kotlinx.datetime.Instant`
 * and `Clock` classes that its auth and storage modules reference. Odo is on 0.7.1 and cannot
 * go back, so the library would fail to link on iOS and throw `NoClassDefFoundError` on
 * Android. Ktor has no kotlinx-datetime dependency, so there is nothing to reconcile.
 *
 * The engine comes in as a parameter — [supabaseHttpClientEngine] supplies OkHttp on Android
 * and Darwin on iOS, and a test passes Ktor's `MockEngine` instead.
 */
internal fun supabaseHttpClient(
    engine: HttpClientEngine,
    environment: SupabaseEnvironment,
    telemetry: SupabaseTelemetry,
): HttpClient = HttpClient(engine) {

    // A non-2xx is a normal answer here, not an exception: PostgREST puts the reason in the
    // status and the body, and the callers read both before deciding.
    expectSuccess = false

    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }

    // Safe to retry every call this module makes: reads are reads, and both writes are
    // upserts keyed on an id the client generated, so a repeated attempt lands on the same
    // row rather than creating a second one.
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = MAX_RETRIES)
        retryOnExceptionIf(maxRetries = MAX_RETRIES) { _, cause -> cause !is kotlin.coroutines.cancellation.CancellationException }
        exponentialDelay()
        modifyRequest { request ->
            telemetry.retried(resource = request.url.encodedPath, attempt = retryCount)
        }
    }

    defaultRequest {
        // Supabase wants the project key on every request even when a user token is present:
        // `apikey` identifies the project, `Authorization` identifies the caller.
        header(SUPABASE_API_KEY_HEADER, environment.anonKey)
        header(HttpHeaders.Accept, JSON_CONTENT_TYPE)
    }
}

/**
 * The platform HTTP engine. OkHttp on Android, Darwin on iOS — declared as an `expect` so the
 * client factory above stays common code.
 */
internal expect fun supabaseHttpClientEngine(): HttpClientEngine

/**
 * The JSON codec for every payload.
 *
 * `ignoreUnknownKeys` because the server is allowed to grow columns without breaking an
 * installed app, and `explicitNulls = false` because PostgREST treats an omitted key as
 * "leave it alone" on an upsert while an explicit `null` clears the column.
 */
internal val SupabaseJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

internal const val SUPABASE_API_KEY_HEADER = "apikey"
internal const val JSON_CONTENT_TYPE = "application/json"

private const val REQUEST_TIMEOUT_MILLIS = 30_000L
private const val CONNECT_TIMEOUT_MILLIS = 15_000L
private const val SOCKET_TIMEOUT_MILLIS = 30_000L
private const val MAX_RETRIES = 2
