package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import arrow.core.flatMap
import com.hopcape.odo.web.admin.domain.CredentialStatus
import com.hopcape.odo.web.admin.domain.PostingMode
import com.hopcape.odo.web.admin.domain.SlotApproval
import com.hopcape.odo.web.admin.domain.SocialAccount
import com.hopcape.odo.web.admin.domain.SocialFact
import com.hopcape.odo.web.admin.domain.SocialPlatform
import com.hopcape.odo.web.admin.domain.SocialPostRecord
import com.hopcape.odo.web.admin.domain.SocialQueueItem
import com.hopcape.odo.web.admin.domain.SocialRepository
import com.hopcape.odo.web.admin.domain.SocialSettings
import com.hopcape.odo.web.admin.domain.SocialSlot
import com.hopcape.odo.web.admin.domain.TelegramRecipient
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The social pipeline over PostgREST.
 *
 * Two kinds of call, and the difference is deliberate. Configuration is ordinary table work
 * against `public.social_*`, whose RLS answers to `blog.write`. Anything that reaches into the
 * `social` schema — the queue, the log, the fact bank — goes through a view or a function,
 * because the panel cannot address that schema and should not be able to.
 *
 * **A token is written and never read.** `connectAccount` stores the account row and then
 * calls `set_social_credential`, and nothing here can select one back. That asymmetry is the
 * whole security posture: an admin session that leaked would leak no Instagram token with it.
 */
internal class SupabaseSocialRepository(
    private val postgrest: Postgrest,
    /**
     * This project's own address and publishable key.
     *
     * Passed to `arm_social_tick` rather than read from Vault inside it: the panel is already
     * talking to exactly the project the schedule should point at, so the button cannot arm
     * the wrong one — which is the mistake a URL typed by hand makes.
     */
    private val projectUrl: String,
    private val projectKey: String,
) : SocialRepository {

    /* ------------------------------ settings ------------------------------ */

    override suspend fun settings(): Either<WebError, SocialSettings> =
        postgrest.select(
            table = SETTINGS,
            serializer = SettingsRow.serializer(),
            query = "select=posting_mode,paused,timezone,updated_at&limit=1",
        ).map { rows ->
            rows.firstOrNull()?.let {
                SocialSettings(
                    mode = PostingMode.ofId(it.postingMode),
                    paused = it.paused,
                    timezone = it.timezone,
                    updatedAt = it.updatedAt.readableTimestamp(),
                )
            } ?: SocialSettings()
        }

    override suspend fun saveSettings(settings: SocialSettings): Either<WebError, Unit> =
        postgrest.patch(
            table = SETTINGS,
            query = "id=eq.true",
            body = """{"posting_mode":"${settings.mode.id}",""" +
                """"paused":${settings.paused},""" +
                """"timezone":"${settings.timezone.jsonEscaped()}"}""",
        )

    /* ------------------------------ schedule ------------------------------ */

    override suspend fun slots(): Either<WebError, List<SocialSlot>> =
        postgrest.select(
            table = SCHEDULE,
            serializer = SlotRow.serializer(),
            query = "select=id,label,time_of_day,days_of_week,day_of_month,platforms," +
                "variant,include_story,approval,enabled,last_fired_at&order=time_of_day.asc",
        ).map { rows -> rows.map(SlotRow::toSlot) }

    override suspend fun saveSlot(slot: SocialSlot): Either<WebError, Unit> {
        val body = buildString {
            append("{")
            if (slot.id.isNotBlank()) append(""""id":"${slot.id.jsonEscaped()}",""")
            append(""""label":"${slot.label.jsonEscaped()}",""")
            append(""""time_of_day":"${slot.timeOfDay.jsonEscaped()}",""")
            append(""""days_of_week":${slot.daysOfWeek.joinToString(",", "[", "]")},""")
            append(""""day_of_month":${slot.dayOfMonth ?: "null"},""")
            append(""""platforms":${slot.platforms.joinToString(",", "[", "]") { "\"${it.id}\"" }},""")
            append(""""include_story":${slot.includeStory},""")
            append(""""approval":"${slot.approval.id}",""")
            append(""""enabled":${slot.enabled}""")
            append("}")
        }
        // Merge-duplicates so the same call creates and edits: the form does not need to know
        // which it is, and a slot with no id is simply one the database has not named yet.
        return postgrest.upsert(
            table = SCHEDULE,
            body = "[$body]",
            serializer = SlotRow.serializer(),
        ).map { }
    }

    override suspend fun deleteSlot(id: String): Either<WebError, Unit> =
        postgrest.delete(table = SCHEDULE, query = "id=eq.${id.socialUrlEncoded()}")

    /* ------------------------------ accounts ------------------------------ */

    override suspend fun accounts(): Either<WebError, List<SocialAccount>> =
        postgrest.select(
            table = ACCOUNTS,
            serializer = AccountRow.serializer(),
            query = "select=id,platform,display_name,external_id,enabled,token_expires_at," +
                "connected_at&order=platform.asc",
        ).map { rows ->
            rows.map {
                SocialAccount(
                    id = it.id,
                    platform = SocialPlatform.ofId(it.platform),
                    displayName = it.displayName,
                    externalId = it.externalId,
                    enabled = it.enabled,
                    tokenExpiresAt = it.tokenExpiresAt?.readableTimestamp(),
                    connectedAt = it.connectedAt.readableTimestamp(),
                )
            }
        }

    /**
     * The row first, then the token against the id it came back with.
     *
     * Two calls rather than one, because they go to different places for different reasons:
     * the row is readable and the token is not. A failure between them leaves an account with
     * no token, which the accounts screen shows as exactly that.
     */
    override suspend fun connectAccount(account: SocialAccount, token: String): Either<WebError, Unit> =
        postgrest.upsert(
            table = ACCOUNTS,
            body = """[{"platform":"${account.platform.id}",""" +
                """"display_name":"${account.displayName.jsonEscaped()}",""" +
                """"external_id":"${account.externalId.jsonEscaped()}",""" +
                """"token_expires_at":${account.tokenExpiresAt?.let { "\"$it\"" } ?: "null"}}]""",
            serializer = AccountRow.serializer(),
            // The natural key, not the surrogate one: reconnecting the same page is the same
            // account with a new token, not a second row for it.
            onConflict = "platform,external_id",
        ).flatMap { rows ->
            // An empty representation means the row did not come back, so there is no id to
            // file the token against. Reported rather than swallowed: the previous version
            // answered success and left an account with no token, which reads on screen as a
            // UI glitch rather than a write that did not happen.
            val id = rows.firstOrNull()?.id
                ?: return@flatMap Either.Left(WebError.Unexpected("the account was not stored"))
            setCredential(CredentialStatus.forAccount(id), token)
        }

    override suspend fun setAccountEnabled(id: String, enabled: Boolean): Either<WebError, Unit> =
        postgrest.patch(
            table = ACCOUNTS,
            query = "id=eq.${id.socialUrlEncoded()}",
            body = """{"enabled":$enabled}""",
        )

    /** The row goes, and its token with it — a credential nothing addresses is a leak in waiting. */
    override suspend fun disconnectAccount(id: String): Either<WebError, Unit> =
        clearCredential(CredentialStatus.forAccount(id))
            .flatMap { postgrest.delete(table = ACCOUNTS, query = "id=eq.${id.socialUrlEncoded()}") }

    /* ------------------------------ recipients ------------------------------ */

    override suspend fun recipients(): Either<WebError, List<TelegramRecipient>> =
        postgrest.select(
            table = RECIPIENTS,
            serializer = RecipientRow.serializer(),
            query = "select=chat_id,name,notify,can_approve&order=name.asc",
        ).map { rows ->
            rows.map { TelegramRecipient(it.chatId, it.name, it.notify, it.canApprove) }
        }

    override suspend fun saveRecipient(recipient: TelegramRecipient): Either<WebError, Unit> =
        postgrest.upsert(
            table = RECIPIENTS,
            body = """[{"chat_id":${recipient.chatId},""" +
                """"name":"${recipient.name.jsonEscaped()}",""" +
                """"notify":${recipient.notify},""" +
                """"can_approve":${recipient.canApprove}}]""",
            serializer = RecipientRow.serializer(),
            onConflict = "chat_id",
        ).map { }

    override suspend fun removeRecipient(chatId: Long): Either<WebError, Unit> =
        postgrest.delete(table = RECIPIENTS, query = "chat_id=eq.$chatId")

    /* ------------------------------ queue & log ------------------------------ */

    override suspend fun queue(): Either<WebError, List<SocialQueueItem>> =
        postgrest.select(
            table = QUEUE_VIEW,
            serializer = QueueRow.serializer(),
            query = "select=id,status,variant,include_story,copy,post_image_url," +
                "story_image_url,error,created_at&order=created_at.desc&limit=50",
        ).map { rows -> rows.map(QueueRow::toItem) }

    override suspend fun setQueueStatus(id: Long, status: String): Either<WebError, Unit> =
        postgrest.call(name = "set_social_queue_status", body = """{"p_id":$id,"p_status":"$status"}""")

    override suspend fun postLog(): Either<WebError, List<SocialPostRecord>> =
        postgrest.select(
            table = LOG_VIEW,
            serializer = LogRow.serializer(),
            query = "select=id,queue_id,ig_media_id,ig_story_id,published_at" +
                "&order=published_at.desc&limit=50",
        ).map { rows ->
            rows.map {
                SocialPostRecord(
                    id = it.id,
                    queueId = it.queueId,
                    mediaId = it.mediaId,
                    storyId = it.storyId,
                    publishedAt = it.publishedAt.readableTimestamp(),
                )
            }
        }

    /* ------------------------------ fact bank ------------------------------ */

    override suspend fun facts(): Either<WebError, List<SocialFact>> =
        postgrest.select(
            table = BANK_VIEW,
            serializer = FactRow.serializer(),
            query = "select=id,category,fact,stats,cta,last_used_at&order=last_used_at.asc",
        ).map { rows -> rows.map(FactRow::toFact) }

    override suspend fun saveFact(fact: SocialFact): Either<WebError, Unit> =
        postgrest.call(
            name = "upsert_social_fact",
            body = """{"p_id":${fact.id ?: "null"},""" +
                """"p_category":"${fact.category.jsonEscaped()}",""" +
                """"p_fact":"${fact.fact.jsonEscaped()}",""" +
                """"p_stats":${fact.stats.toStatsJson()},""" +
                """"p_cta":"${fact.cta.jsonEscaped()}"}""",
        )

    override suspend fun deleteFact(id: Long): Either<WebError, Unit> =
        postgrest.call(name = "delete_social_fact", body = """{"p_id":$id}""")

    /* ------------------------------ the scheduler ------------------------------ */

    override suspend fun tickSchedule(): Either<WebError, String> =
        postgrest.rpcOne(name = "social_tick_status", serializer = String.serializer(), body = "{}")
            .map { it.orEmpty() }

    override suspend fun armTick(): Either<WebError, Unit> =
        postgrest.call(
            name = "arm_social_tick",
            body = """{"p_url":"${projectUrl.jsonEscaped()}","p_key":"${projectKey.jsonEscaped()}"}""",
        )

    override suspend fun disarmTick(): Either<WebError, Unit> =
        postgrest.call(name = "disarm_social_tick", body = "{}")

    /* ------------------------------ credentials ------------------------------ */

    override suspend fun credentials(): Either<WebError, List<CredentialStatus>> =
        postgrest.select(
            table = CREDENTIAL_STATUS_VIEW,
            serializer = CredentialRow.serializer(),
            query = "select=key,updated_at",
        ).map { rows -> rows.map { CredentialStatus(it.key, it.updatedAt.readableTimestamp()) } }

    override suspend fun setCredential(key: String, value: String): Either<WebError, Unit> =
        postgrest.call(
            name = "set_social_credential",
            body = """{"p_key":"${key.jsonEscaped()}","p_value":"${value.jsonEscaped()}"}""",
        )

    override suspend fun clearCredential(key: String): Either<WebError, Unit> =
        postgrest.call(name = "clear_social_credential", body = """{"p_key":"${key.jsonEscaped()}"}""")

    private companion object {
        const val SETTINGS = "social_settings"
        const val SCHEDULE = "social_schedule"
        const val ACCOUNTS = "social_accounts"
        const val RECIPIENTS = "social_telegram_recipients"
        const val QUEUE_VIEW = "social_queue"
        const val LOG_VIEW = "social_post_log"
        const val BANK_VIEW = "social_content_bank"
        const val CREDENTIAL_STATUS_VIEW = "social_credential_status"
    }
}

/* ------------------------------ rows ------------------------------ */

@Serializable
private data class SettingsRow(
    @SerialName("posting_mode") val postingMode: String,
    val paused: Boolean = false,
    val timezone: String = "Asia/Kolkata",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
private data class SlotRow(
    val id: String,
    val label: String,
    @SerialName("time_of_day") val timeOfDay: String,
    @SerialName("days_of_week") val daysOfWeek: List<Int> = emptyList(),
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    val platforms: List<String> = emptyList(),
    val variant: String = "stat",
    @SerialName("include_story") val includeStory: Boolean = false,
    val approval: String = "manual",
    val enabled: Boolean = true,
    @SerialName("last_fired_at") val lastFiredAt: String? = null,
) {
    fun toSlot() = SocialSlot(
        id = id,
        label = label,
        // Postgres hands back `09:00:00`; the form and the tick both work in minutes.
        timeOfDay = timeOfDay.take(5),
        daysOfWeek = daysOfWeek,
        dayOfMonth = dayOfMonth,
        platforms = platforms.map(SocialPlatform::ofId),
        includeStory = includeStory,
        approval = SlotApproval.ofId(approval),
        enabled = enabled,
        lastFiredAt = lastFiredAt?.readableTimestamp(),
    )
}

@Serializable
private data class AccountRow(
    val id: String = "",
    val platform: String = "other",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("external_id") val externalId: String = "",
    val enabled: Boolean = true,
    @SerialName("token_expires_at") val tokenExpiresAt: String? = null,
    @SerialName("connected_at") val connectedAt: String = "",
)

@Serializable
private data class RecipientRow(
    @SerialName("chat_id") val chatId: Long,
    val name: String = "",
    val notify: Boolean = true,
    @SerialName("can_approve") val canApprove: Boolean = false,
)

@Serializable
private data class QueueRow(
    val id: Long,
    val status: String,
    val variant: String = "stat",
    @SerialName("include_story") val includeStory: Boolean = false,
    val copy: JsonElement? = null,
    @SerialName("post_image_url") val postImageUrl: String? = null,
    @SerialName("story_image_url") val storyImageUrl: String? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String = "",
) {
    fun toItem() = SocialQueueItem(
        id = id,
        status = status,
        variant = variant,
        includeStory = includeStory,
        headline = copy.text("headline"),
        caption = copy.text("caption"),
        hashtags = copy.text("hashtags"),
        postImageUrl = postImageUrl,
        storyImageUrl = storyImageUrl,
        error = error,
        createdAt = createdAt.readableTimestamp(),
    )
}

@Serializable
private data class LogRow(
    val id: Long,
    @SerialName("queue_id") val queueId: Long? = null,
    @SerialName("ig_media_id") val mediaId: String? = null,
    @SerialName("ig_story_id") val storyId: String? = null,
    @SerialName("published_at") val publishedAt: String = "",
)

@Serializable
private data class FactRow(
    val id: Long,
    val category: String = "",
    val fact: String = "",
    val stats: JsonElement? = null,
    val cta: String = "",
    @SerialName("last_used_at") val lastUsedAt: String? = null,
) {
    fun toFact() = SocialFact(
        id = id,
        category = category,
        fact = fact,
        stats = stats.toStatPairs(),
        cta = cta,
        lastUsedAt = lastUsedAt?.readableTimestamp(),
    )
}

@Serializable
private data class CredentialRow(
    val key: String,
    @SerialName("updated_at") val updatedAt: String = "",
)

/* ------------------------------ json helpers ------------------------------ */

/**
 * A string out of the model's own JSON, or empty.
 *
 * The copy column is whatever Gemini returned, so a key can be missing on an older row.
 * Absent reads as blank rather than as a failure: a caption nobody wrote is a caption nobody
 * wrote, and it must not take a queue screen down with it.
 */
private fun JsonElement?.text(key: String): String =
    runCatching { this?.jsonObject?.get(key)?.jsonPrimitive?.content }.getOrNull().orEmpty()

/** `[{label, value}]` as the card's pairs, dropping anything that is not one. */
private fun JsonElement?.toStatPairs(): List<Pair<String, String>> = runCatching {
    (this as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val value = obj["value"]?.jsonPrimitive?.content ?: return@mapNotNull null
        label to value
    }
}.getOrDefault(emptyList())

private fun List<Pair<String, String>>.toStatsJson(): String =
    joinToString(",", "[", "]") { (label, value) ->
        """{"label":"${label.jsonEscaped()}","value":"${value.jsonEscaped()}"}"""
    }

/** `2026-09-05T10:15:00.123Z` as `2026-09-05 10:15:00`, which is what a table cell wants. */
private fun String.readableTimestamp(): String = replace('T', ' ').substringBefore('.')

/**
 * Percent-encodes anything that is not a bare word.
 *
 * The ids here are uuids and would survive unencoded. Encoded anyway, because the check that
 * guarantees that lives in the database and a filter built by string concatenation is the
 * wrong place to rely on it — the same reasoning `SupabaseFlagsRepository` states for its key.
 */
private fun String.socialUrlEncoded(): String =
    buildString {
        for (c in this@socialUrlEncoded) {
            if (c.isLetterOrDigit() || c == '_' || c == '-') append(c) else append('%').append(c.code.toString(16))
        }
    }
