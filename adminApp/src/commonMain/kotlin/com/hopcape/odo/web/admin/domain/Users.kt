package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/** What an account may be stopped from doing. Mirrors `profiles.restriction`. */
enum class Restriction(val id: String) {
    None("none"),

    /** The server refuses their writes; the local app keeps working, with a banner. */
    ReadOnly("read_only"),

    /** `firebase-session` refuses to mint a session, so they cannot sign back in. */
    Blocked("blocked"),
    ;

    companion object {
        fun ofId(id: String?): Restriction = entries.firstOrNull { it.id == id } ?: None
    }
}

/** An entitlement granted or withheld outside the store. */
data class EntitlementOverride(
    val feature: String,
    val granted: Boolean,
    val expiresAt: String?,
    val reason: String,
    val grantedAt: String,
)

/** One account, as the support screen shows it. */
data class ManagedUser(
    val id: String,
    val phone: String?,
    val email: String?,
    val restriction: Restriction,
    val restrictionReason: String?,
    val createdAt: String,
    val entitlements: List<EntitlementOverride>,
)

/**
 * Looking somebody up, and changing what they may do.
 *
 * [find] is exact-match by design — a support tool that lists every account
 * matching a few typed digits is an enumeration tool, and the question support
 * actually has is "who is this".
 */
interface UsersRepository {

    /** Null when nothing matches that phone, email or id. */
    suspend fun find(query: String): Either<WebError, ManagedUser?>

    /**
     * Grant or revoke a feature for one owner.
     *
     * [granted] false is a deliberate revoke, stored as a row rather than the
     * absence of one: with the store saying yes, "cut off" and "never looked at"
     * have to be told apart.
     */
    suspend fun setEntitlement(
        ownerId: String,
        feature: String,
        granted: Boolean,
        reason: String,
    ): Either<WebError, Unit>

    /** Removes the override entirely, so the store's answer stands again. */
    suspend fun clearEntitlement(ownerId: String, feature: String): Either<WebError, Unit>

    suspend fun setRestriction(
        ownerId: String,
        restriction: Restriction,
        reason: String?,
    ): Either<WebError, Unit>
}

/** One line of the audit log. */
data class AuditEntry(
    val id: Long,
    val action: String,
    val subjectType: String,
    val subjectId: String?,
    val actorEmail: String?,
    val at: String,
)

interface AuditRepository {
    suspend fun recent(limit: Int = 200): Either<WebError, List<AuditEntry>>
}
