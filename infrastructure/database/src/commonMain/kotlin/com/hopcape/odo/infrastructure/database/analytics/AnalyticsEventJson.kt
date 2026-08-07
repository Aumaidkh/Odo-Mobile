package com.hopcape.odo.infrastructure.database.analytics

import com.hopcape.analytics.api.StoredAnalyticsContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The `properties_json`/`context_json` codec for `analytics_events`.
 *
 * `StoredAnalyticsContext` itself is not `@Serializable` — it lives in
 * `:observability:analytics`, which stays free of a serialization dependency the same way
 * `:core:domain` does (see `FairnessSnapshotJson`'s KDoc for the same reasoning). This DTO
 * mirrors its fields instead.
 */
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class StoredContextDto(
    @SerialName("app_version") val appVersion: String,
    @SerialName("platform") val platform: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("os_version") val osVersion: String,
    @SerialName("locale") val locale: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("anonymous_id") val anonymousId: String,
    @SerialName("user_id") val userId: String? = null,
)

internal fun StoredAnalyticsContext.toJson(): String = json.encodeToString(
    StoredContextDto(
        appVersion = appVersion,
        platform = platform,
        deviceModel = deviceModel,
        osVersion = osVersion,
        locale = locale,
        sessionId = sessionId,
        anonymousId = anonymousId,
        userId = userId,
    ),
)

/** Rebuilds a context from storage, or null if the column is empty or unreadable. */
internal fun String?.toStoredContextOrNull(): StoredAnalyticsContext? {
    val dto = runCatching { this?.let { json.decodeFromString<StoredContextDto>(it) } }.getOrNull() ?: return null
    return StoredAnalyticsContext(
        appVersion = dto.appVersion,
        platform = dto.platform,
        deviceModel = dto.deviceModel,
        osVersion = dto.osVersion,
        locale = dto.locale,
        sessionId = dto.sessionId,
        anonymousId = dto.anonymousId,
        userId = dto.userId,
    )
}

/**
 * Encodes an event's properties for storage. Values are normalized to
 * String/Long/Double/Boolean/null — the same primitive set every real destination
 * (Firebase, PostHog) ultimately accepts — so a caller tracking an unsupported type (a
 * custom object, a nested collection) is coerced here rather than failing deep inside a
 * vendor SDK's own serializer on eventual delivery. `Int` widens to `Long` and `Float` to
 * `Double`, matching `FirebaseEventSanitizer`'s own coercions.
 */
internal fun Map<String, Any?>.toPropertiesJson(): String =
    json.encodeToString(JsonObject.serializer(), JsonObject(mapValues { (_, value) -> value.toJsonElement() }))

/** Rebuilds properties from storage, or null if the column is empty or unreadable. */
internal fun String?.toPropertiesOrNull(): Map<String, Any?>? =
    runCatching { this?.let { json.parseToJsonElement(it).jsonObject } }
        .getOrNull()
        ?.mapValues { (_, element) -> element.toValue() }

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this.toLong())
    is Double -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this.toDouble())
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(this.toString())
}

private fun JsonElement.toValue(): Any? {
    if (this is JsonNull) return null
    val primitive = jsonPrimitive
    primitive.booleanOrNull?.let { return it }
    primitive.longOrNull?.let { return it }
    primitive.doubleOrNull?.let { return it }
    return primitive.contentOrNull
}
