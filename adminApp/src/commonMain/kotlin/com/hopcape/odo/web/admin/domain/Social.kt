package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/**
 * The social pipeline, as the panel sees it.
 *
 * Everything here is reached through `public` — views and functions over the `social` schema —
 * because the panel's Postgrest sends no `Accept-Profile` header and because a browser that
 * could read those tables directly could read an Instagram token out of one.
 * `20260905200000_social_admin.sql` is where that shape is decided.
 */

/**
 * How the pipeline decides to post.
 *
 * [SCHEDULED] is the only mode under which a slot's own approval means anything: the other
 * two have already answered the question, and a per-slot setting that disagreed with the
 * mode would be a setting that contradicts itself.
 */
enum class PostingMode(val id: String) {
    /** Whatever is generated goes out. Nobody is asked. */
    AUTO("auto"),

    /** Nothing runs on its own; a post is made on demand and always waits for a person. */
    CUSTOM("custom"),

    /** The schedule drives it, and each slot says whether it needs approving. */
    SCHEDULED("scheduled"),
    ;

    companion object {
        fun ofId(id: String?): PostingMode = entries.firstOrNull { it.id == id } ?: SCHEDULED
    }
}

/** Whether a scheduled slot's post waits for a person. */
enum class SlotApproval(val id: String) {
    MANUAL("manual"),
    AUTO("auto"),
    ;

    companion object {
        fun ofId(id: String?): SlotApproval = entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

/** Where a post can go. `OTHER` exists so a new network needs a row, not a release. */
enum class SocialPlatform(val id: String, val label: String) {
    INSTAGRAM("instagram", "Instagram"),
    FACEBOOK("facebook", "Facebook"),
    TELEGRAM("telegram", "Telegram"),
    OTHER("other", "Other"),
    ;

    companion object {
        fun ofId(id: String?): SocialPlatform = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

data class SocialSettings(
    val mode: PostingMode = PostingMode.SCHEDULED,
    /** Stops generate, render and publish. Not the same as deleting the schedule. */
    val paused: Boolean = false,
    val timezone: String = "Asia/Kolkata",
    val updatedAt: String = "",
)

/**
 * One slot on the calendar.
 *
 * [daysOfWeek] is ISO — 1 is Monday — and empty means every day. [dayOfMonth] is null unless
 * the slot is monthly. Both set means both must match.
 */
data class SocialSlot(
    val id: String,
    val label: String,
    /** `HH:MM`, local to [SocialSettings.timezone]. */
    val timeOfDay: String,
    val daysOfWeek: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    /** Empty means every connected account. */
    val platforms: List<SocialPlatform> = emptyList(),
    val includeStory: Boolean = false,
    val approval: SlotApproval = SlotApproval.MANUAL,
    val enabled: Boolean = true,
    val lastFiredAt: String? = null,
)

/**
 * A connected account, with no token in it.
 *
 * The token lives in `social_credentials`, which the panel can write and never read. What is
 * here is what a screen may show: what it is, what it is called, and when it stops working.
 */
data class SocialAccount(
    val id: String,
    val platform: SocialPlatform,
    val displayName: String,
    /** The IG business user id, the FB page id — whatever the API is addressed by. */
    val externalId: String,
    val enabled: Boolean = true,
    /** Null when the platform issues no expiring token. */
    val tokenExpiresAt: String? = null,
    val connectedAt: String = "",
)

/** A Telegram chat the pipeline talks to, and what it is allowed to do back. */
data class TelegramRecipient(
    val chatId: Long,
    val name: String,
    val notify: Boolean = true,
    /** Until this is true, pressing a button in Telegram does nothing. */
    val canApprove: Boolean = false,
)

/** A post waiting on somebody, or one that failed. */
data class SocialQueueItem(
    val id: Long,
    val status: String,
    val variant: String,
    val includeStory: Boolean,
    val headline: String,
    val caption: String,
    val hashtags: String,
    val postImageUrl: String?,
    val storyImageUrl: String?,
    val error: String?,
    val createdAt: String,
) {
    val isPending: Boolean get() = status == "rendered" || status == "draft"
    val hasFailed: Boolean get() = status == "failed" || !error.isNullOrBlank()
}

/** What went live, and where. */
data class SocialPostRecord(
    val id: Long,
    val queueId: Long?,
    val mediaId: String?,
    val storyId: String?,
    val publishedAt: String,
)

/** A verified fact the model is allowed to write copy around. It never invents a number. */
data class SocialFact(
    val id: Long?,
    val category: String,
    val fact: String,
    /** Up to three `label: value` pairs, shown on the card. */
    val stats: List<Pair<String, String>> = emptyList(),
    val cta: String = "",
    val lastUsedAt: String? = null,
)

/** Which secrets are set, and when. Never their values. */
data class CredentialStatus(val key: String, val updatedAt: String) {
    companion object {
        const val GEMINI = "gemini_api_key"
        const val TELEGRAM_BOT = "telegram_bot_token"

        /** The token for one connected account. */
        fun forAccount(id: String): String = "account:$id"
    }
}

interface SocialRepository {

    suspend fun settings(): Either<WebError, SocialSettings>
    suspend fun saveSettings(settings: SocialSettings): Either<WebError, Unit>

    suspend fun slots(): Either<WebError, List<SocialSlot>>
    suspend fun saveSlot(slot: SocialSlot): Either<WebError, Unit>
    suspend fun deleteSlot(id: String): Either<WebError, Unit>

    suspend fun accounts(): Either<WebError, List<SocialAccount>>
    /** Stores the account and its token together; the token never comes back. */
    suspend fun connectAccount(account: SocialAccount, token: String): Either<WebError, Unit>
    suspend fun setAccountEnabled(id: String, enabled: Boolean): Either<WebError, Unit>
    suspend fun disconnectAccount(id: String): Either<WebError, Unit>

    suspend fun recipients(): Either<WebError, List<TelegramRecipient>>
    suspend fun saveRecipient(recipient: TelegramRecipient): Either<WebError, Unit>
    suspend fun removeRecipient(chatId: Long): Either<WebError, Unit>

    suspend fun queue(): Either<WebError, List<SocialQueueItem>>
    suspend fun setQueueStatus(id: Long, status: String): Either<WebError, Unit>

    suspend fun postLog(): Either<WebError, List<SocialPostRecord>>

    suspend fun facts(): Either<WebError, List<SocialFact>>
    suspend fun saveFact(fact: SocialFact): Either<WebError, Unit>
    suspend fun deleteFact(id: Long): Either<WebError, Unit>

    suspend fun credentials(): Either<WebError, List<CredentialStatus>>
    suspend fun setCredential(key: String, value: String): Either<WebError, Unit>
    suspend fun clearCredential(key: String): Either<WebError, Unit>
}
