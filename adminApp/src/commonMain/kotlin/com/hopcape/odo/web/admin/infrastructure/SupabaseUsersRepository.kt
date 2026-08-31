package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import com.hopcape.odo.web.admin.domain.AuditEntry
import com.hopcape.odo.web.admin.domain.AuditRepository
import com.hopcape.odo.web.admin.domain.EntitlementOverride
import com.hopcape.odo.web.admin.domain.ManagedUser
import com.hopcape.odo.web.admin.domain.Restriction
import com.hopcape.odo.web.admin.domain.UsersRepository
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.encoded
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `profiles` and `entitlement_overrides` over PostgREST.
 *
 * The lookup is an RPC because it assembles one row from `profiles` and
 * `auth.users`, and `auth.users` is readable by no client role and must stay
 * that way. Everything else is an ordinary table write behind
 * `users.entitlements.write` or `users.restrict.write`.
 */
internal class SupabaseUsersRepository(
    private val postgrest: Postgrest,
) : UsersRepository {

    override suspend fun find(query: String): Either<WebError, ManagedUser?> =
        postgrest.rpcOne(
            name = "admin_find_user",
            body = """{"p_query":"${query.jsonEscaped()}"}""",
            serializer = UserRow.serializer(),
        ).map { row -> row?.toUser() }

    override suspend fun setEntitlement(
        ownerId: String,
        feature: String,
        granted: Boolean,
        reason: String,
    ): Either<WebError, Unit> = postgrest.upsert(
        table = TABLE_OVERRIDES,
        // The composite key, so granting the same feature twice edits the row
        // rather than colliding with it.
        onConflict = "owner_id,feature",
        serializer = EmptyRow.serializer(),
        body = """{"owner_id":"$ownerId","feature":"${feature.jsonEscaped()}",""" +
            """"granted":$granted,"reason":"${reason.jsonEscaped()}"}""",
    ).map { }

    override suspend fun clearEntitlement(ownerId: String, feature: String): Either<WebError, Unit> =
        postgrest.delete(
            table = TABLE_OVERRIDES,
            query = "owner_id=eq.$ownerId&feature=eq.${feature.encoded()}",
        )

    override suspend fun setRestriction(
        ownerId: String,
        restriction: Restriction,
        reason: String?,
    ): Either<WebError, Unit> {
        // Explicit nulls: PostgREST reads an absent key as "leave this column
        // alone", so lifting a restriction without clearing the reason would keep
        // the old one attached to an account that is no longer restricted.
        val reasonJson = reason?.let { "\"${it.jsonEscaped()}\"" } ?: "null"
        val at = if (restriction == Restriction.None) "null" else "\"now()\""
        return postgrest.patch(
            table = TABLE_PROFILES,
            query = "id=eq.$ownerId",
            body = """{"restriction":"${restriction.id}","restriction_reason":$reasonJson,"restricted_at":$at}""",
        )
    }

    private companion object {
        const val TABLE_PROFILES = "profiles"
        const val TABLE_OVERRIDES = "entitlement_overrides"
    }
}

/** `admin_audit_log`, newest first. */
internal class SupabaseAuditRepository(
    private val postgrest: Postgrest,
) : AuditRepository {

    override suspend fun recent(limit: Int): Either<WebError, List<AuditEntry>> =
        postgrest.select(
            table = "admin_audit_log",
            serializer = AuditRow.serializer(),
            // The actor is embedded rather than fetched per row. Two foreign keys
            // point at admin_users from admin_user_roles, but only one does from
            // here, so this embed needs no disambiguation — unlike that one, which
            // answers PGRST201 without it.
            query = "select=id,action,subject_type,subject_id,at,admin_users(email)" +
                "&order=at.desc&limit=$limit",
        ).map { rows ->
            rows.map {
                AuditEntry(
                    id = it.id,
                    action = it.action,
                    subjectType = it.subjectType,
                    subjectId = it.subjectId,
                    actorEmail = it.actor?.email,
                    at = it.at.replace('T', ' ').substringBefore('.'),
                )
            }
        }
}

@Serializable
private data class UserRow(
    val id: String,
    val phone: String? = null,
    val email: String? = null,
    val restriction: String? = null,
    @SerialName("restriction_reason") val restrictionReason: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    val entitlements: List<OverrideRow> = emptyList(),
) {
    fun toUser() = ManagedUser(
        id = id,
        phone = phone,
        email = email,
        restriction = Restriction.ofId(restriction),
        restrictionReason = restrictionReason,
        createdAt = createdAt.substringBefore('T'),
        entitlements = entitlements.map {
            EntitlementOverride(
                feature = it.feature,
                granted = it.granted,
                expiresAt = it.expiresAt?.substringBefore('T'),
                reason = it.reason,
                grantedAt = it.grantedAt.substringBefore('T'),
            )
        },
    )
}

@Serializable
private data class OverrideRow(
    val feature: String,
    val granted: Boolean,
    @SerialName("expires_at") val expiresAt: String? = null,
    val reason: String = "",
    @SerialName("granted_at") val grantedAt: String = "",
)

@Serializable
private data class AuditRow(
    val id: Long,
    val action: String,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("subject_id") val subjectId: String? = null,
    val at: String,
    @SerialName("admin_users") val actor: ActorRow? = null,
)

@Serializable
private data class ActorRow(val email: String? = null)

/** PostgREST hands back the row it wrote; nothing here reads it. */
@Serializable
private class EmptyRow
