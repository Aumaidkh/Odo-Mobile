package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.domain.advisory.BillLineClassifier
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.infrastructure.supabase.http.SupabaseJson
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * `POST /functions/v1/advisory-classify` — the model naming what the rules could not.
 *
 * An Edge Function rather than a direct call, because the Gemini key must stay off the device
 * and the spend cap has to be one number for everybody rather than one per install. The
 * function answers most requests from its own cache and never returns a price.
 *
 * **Every failure is silence.** No session, no configuration, a timeout, a 500, a body nobody
 * could parse: all of them come back as an empty map, and the lines stay unchecked exactly as
 * they would have without this. Nothing here can put an error on the owner's screen.
 *
 * Only the line wording is sent — no plate, no workshop name, no amount. The amounts are
 * withheld deliberately as well as for privacy: a model that never sees a price cannot anchor
 * on one.
 */
internal class SupabaseBillLineClassifier(
    private val client: HttpClient,
    private val environment: SupabaseEnvironment,
    private val tokens: AccessTokenProvider,
    private val telemetry: SupabaseTelemetry,
) : BillLineClassifier {

    override suspend fun classify(labels: List<String>): Map<String, String> {
        if (labels.isEmpty() || !environment.isConfigured) return emptyMap()
        // The function reads the owner out of the token and meters against them. Without a
        // session it can only answer 401, so the round trip is spent to be told nothing.
        val token = tokens.currentAccessToken() ?: return emptyMap()

        return telemetry.span(OPERATION, RESOURCE) {
            try {
                send(labels, token)
            } catch (cancellation: CancellationException) {
                // The owner left the screen. Not a failure, and not worth reporting as one.
                throw cancellation
            } catch (e: Exception) {
                telemetry.failed(OPERATION, RESOURCE, e)
                emptyMap()
            }
        }
    }

    private suspend fun send(labels: List<String>, token: String): Map<String, String> {
        val response = client.post("${environment.functionsUrl}/$FUNCTION") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                SupabaseJson.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(
                        mapOf(
                            LINES to SupabaseJson.encodeToJsonElement(
                                ListSerializer(String.serializer()),
                                labels,
                            ),
                        ),
                    ),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            telemetry.rejected(OPERATION, RESOURCE, response.status.value)
            return emptyMap()
        }

        val body = SupabaseJson.decodeFromString(
            ClassifyResponse.serializer(),
            response.bodyAsText(),
        )
        // Worth counting on its own: capped means the model was not asked, so a run of these
        // explains lines going unchecked without anything having failed.
        if (body.capped) telemetry.rows(OPERATION, RESOURCE_CAPPED, labels.size)
        telemetry.rows(OPERATION, RESOURCE, body.classified.size)
        return body.classified
    }

    private companion object {
        const val RESOURCE = "advisory-classify"

        /** Counted apart from an answer, so a spent budget is visible rather than inferred. */
        const val RESOURCE_CAPPED = "advisory-classify.capped"
        const val OPERATION = "advisory.classify"

        /** The slug the function is deployed under. */
        const val FUNCTION = "advisory-classify"
        const val LINES = "lines"
    }
}

/**
 * Label to category slug, in the caller's own wording.
 *
 * A label the model could not name is absent rather than null-valued, so the map is only ever
 * answers. `capped` says the daily budget was spent and the model was not asked — the cached
 * answers still came back.
 */
@Serializable
private data class ClassifyResponse(
    @SerialName("classified") val classified: Map<String, String> = emptyMap(),
    @SerialName("capped") val capped: Boolean = false,
)
