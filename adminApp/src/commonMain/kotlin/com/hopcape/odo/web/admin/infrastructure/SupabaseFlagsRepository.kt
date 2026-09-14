package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import com.hopcape.odo.web.admin.domain.FeatureFlag
import com.hopcape.odo.web.admin.domain.FlagsRepository
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `app_config`, one row per key.
 *
 * Ordinary Postgrest, unlike the edge function this replaced. There is no ETag and
 * no compare-and-set because there is nothing to collide over: Remote Config
 * replaces its whole template on every write, so two people saving at once lost
 * each other's changes unless the ETag caught it. A row update touches one row.
 */
internal class SupabaseFlagsRepository(
    private val postgrest: Postgrest,
) : FlagsRepository {

    override suspend fun flags(): Either<WebError, List<FeatureFlag>> =
        postgrest.select(
            table = TABLE,
            serializer = FlagRow.serializer(),
            // Parked rows included: this is the screen somebody restores them from,
            // and a row it cannot see is a row it cannot restore.
            query = "select=key,value,value_type,description,owner,is_active,updated_at&order=key.asc",
        ).map { rows ->
            rows.map {
                FeatureFlag(
                    key = it.key,
                    value = it.value,
                    description = it.description,
                    owner = it.owner,
                    valueType = it.valueType,
                    isActive = it.isActive,
                    updatedAt = it.updatedAt.replace('T', ' ').substringBefore('.'),
                )
            }
        }

    override suspend fun set(key: String, value: String): Either<WebError, Unit> =
        postgrest.patch(
            table = TABLE,
            query = "key=eq.${key.urlEncoded()}",
            body = """{"value":"${value.jsonEscaped()}"}""",
        )

    override suspend fun setActive(key: String, active: Boolean): Either<WebError, Unit> =
        postgrest.patch(
            table = TABLE,
            query = "key=eq.${key.urlEncoded()}",
            body = """{"is_active":$active}""",
        )

    private companion object {
        const val TABLE = "app_config"
    }
}

/**
 * Keys are `[a-z][a-z0-9_]*`, so nothing here needs escaping today.
 *
 * Kept anyway, because the check that guarantees that lives in the database and a
 * filter built by string concatenation is the wrong place to rely on it.
 */
private fun String.urlEncoded(): String =
    buildString {
        for (c in this@urlEncoded) {
            if (c.isLetterOrDigit() || c == '_' || c == '-') append(c) else append('%').append(c.code.toString(16))
        }
    }

@Serializable
private data class FlagRow(
    val key: String,
    val value: String,
    @SerialName("value_type") val valueType: String = "STRING",
    val description: String = "",
    val owner: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at") val updatedAt: String = "",
)
