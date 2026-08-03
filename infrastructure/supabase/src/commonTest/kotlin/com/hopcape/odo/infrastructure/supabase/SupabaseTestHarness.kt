package com.hopcape.odo.infrastructure.supabase

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.odo.infrastructure.supabase.http.AnonAccessTokens
import com.hopcape.odo.infrastructure.supabase.http.supabaseHttpClient
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

/**
 * A Supabase stack wired to Ktor's `MockEngine` — the whole client, telemetry and all, with a
 * scripted server instead of a network.
 *
 * The point is to test the real [PostgrestClient] and the real adapters rather than a
 * hand-written double of them: the things that break here are query strings, headers and
 * payload shapes, and a double would agree with whatever the adapter did.
 */
internal class SupabaseTestHarness(
    private val respond: (HttpRequestData) -> MockResponse,
) {
    /** Non-fatals the stack recorded — what a crash dashboard would have received. */
    val nonFatals = mutableListOf<Throwable>()

    /** Every request the adapters made, in order — the assertions read these. */
    val requests = mutableListOf<HttpRequestData>()

    val environment = SupabaseEnvironment(url = "https://project.supabase.co", anonKey = "anon-key")

    private val engine = MockEngine { request ->
        requests += request
        val scripted = respond(request)
        respond(
            content = ByteReadChannel(scripted.body),
            status = scripted.status,
            headers = headersOf("Content-Type", "application/json"),
        )
    }

    val telemetry = SupabaseTelemetry(
        logger = RecordingLogger,
        tracer = NoopTracer,
        crash = object : CrashRecorder {
            override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
                nonFatals += throwable
            }

            override fun leaveBreadcrumb(tag: String, message: String) = Unit
            override fun setCustomKey(key: String, value: Any?) = Unit
            override fun setUserId(userId: String?) = Unit
        },
    )

    val client: HttpClient = supabaseHttpClient(engine, environment, telemetry)

    val postgrest = PostgrestClient(
        client = client,
        environment = environment,
        tokens = AnonAccessTokens(environment),
        telemetry = telemetry,
    )

    /** The single request made, failing loudly if the count was anything but one. */
    fun onlyRequest(): HttpRequestData {
        check(requests.size == 1) { "expected exactly one request, got ${requests.size}" }
        return requests.first()
    }
}

/** A scripted server answer. */
internal data class MockResponse(
    val body: String,
    val status: HttpStatusCode = HttpStatusCode.OK,
)

/** The JSON a request carried, for asserting on payload shape. */
internal fun HttpRequestData.bodyText(): String = (body as TextContent).text

/**
 * The raw bytes a request carried — what a storage upload sends.
 *
 * Goes through MockEngine's own helper rather than casting: Ktor's default transformers wrap a
 * `ByteArray` body in a channel-backed content, so it is not a `ByteArrayContent` by the time
 * the engine sees it.
 */
internal suspend fun HttpRequestData.bodyBytes(): ByteArray = body.toByteArray()

/** Swallows log output — the tests assert on behaviour, not on log lines. */
internal object RecordingLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit

    override fun flush() = Unit
}

internal object NoopTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        object : Span {
            override val spanId = "test-span"
            override val traceId = traceId
            override val parentSpanId = parentSpanId
            override val name = name
            override fun setAttribute(key: String, value: Any?): Span = this
        }

    override fun endSpan(span: Span) = Unit
    override fun flush() = Unit
}

internal object RecordingCrashRecorder : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}
