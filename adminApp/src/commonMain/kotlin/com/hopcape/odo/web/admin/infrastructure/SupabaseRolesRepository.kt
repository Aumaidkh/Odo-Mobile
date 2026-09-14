package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import com.hopcape.odo.web.admin.domain.AdminRole
import com.hopcape.odo.web.admin.domain.RolesRepository
import com.hopcape.odo.web.admin.domain.StaffMember
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import com.hopcape.odo.web.core.infrastructure.supabase.encoded
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `admin_roles`, `admin_role_permissions` and `admin_user_roles` over PostgREST.
 *
 * Every write here needs `admin.roles.write`, which only super-admin holds. The
 * reads need only `is_admin()`, because the rail has to know what this session
 * may open before it can draw itself.
 */
internal class SupabaseRolesRepository(
    private val postgrest: Postgrest,
    private val invites: SupabaseInvites,
) : RolesRepository {

    override suspend fun invite(email: String): Either<WebError, Unit> = invites.invite(email)

    override suspend fun roles(): Either<WebError, List<AdminRole>> {
        val holders = postgrest.select(
            table = "admin_user_roles",
            serializer = HolderRow.serializer(),
            query = "select=role_slug",
        ).fold({ emptyList() }, { it })
        val counts = holders.groupingBy { it.roleSlug }.eachCount()

        return postgrest.select(
            table = "admin_roles",
            serializer = RoleRow.serializer(),
            query = "select=slug,name,description&order=slug.asc",
        ).map { rows ->
            rows.map { AdminRole(it.slug, it.name, it.description, counts[it.slug] ?: 0) }
        }
    }

    override suspend fun grants(): Either<WebError, Set<Pair<String, String>>> =
        postgrest.select(
            table = "admin_role_permissions",
            serializer = GrantRow.serializer(),
            query = "select=role_slug,permission",
        ).map { rows -> rows.map { it.roleSlug to it.permission }.toSet() }

    override suspend fun setGrant(
        roleSlug: String,
        permission: String,
        granted: Boolean,
    ): Either<WebError, Unit> = if (granted) {
        postgrest.insert(
            table = "admin_role_permissions",
            body = """{"role_slug":"$roleSlug","permission":"$permission"}""",
            // Granting something already granted is the same end state, not a
            // failure worth showing somebody who clicked a cell twice.
            conflictIsFine = true,
        )
    } else {
        postgrest.delete(
            table = "admin_role_permissions",
            query = "role_slug=eq.${roleSlug.encoded()}&permission=eq.${permission.encoded()}",
        )
    }

    override suspend fun createRole(
        slug: String,
        name: String,
        description: String,
    ): Either<WebError, Unit> = postgrest.insert(
        table = "admin_roles",
        body = """{"slug":"${slug.jsonEscaped()}","name":"${name.jsonEscaped()}",""" +
            """"description":"${description.jsonEscaped()}"}""",
    )

    override suspend fun staff(): Either<WebError, List<StaffMember>> =
        postgrest.select(
            table = "admin_users",
            serializer = StaffRow.serializer(),
            // The embed is disambiguated by constraint name, and it has to be: two
            // foreign keys point at admin_users from admin_user_roles — the holder,
            // and whoever granted the role — so PostgREST answers PGRST201 rather
            // than guessing. Without the `!` this call fails outright.
            query = "select=id,email,name,is_active,user_id," +
                "admin_user_roles!admin_user_roles_admin_id_fkey(role_slug)" +
                "&order=email.asc",
        ).map { rows ->
            rows.map { row ->
                StaffMember(
                    id = row.id,
                    email = row.email,
                    // The address is the fallback, not "Unknown": a row added by
                    // email and never signed into has no name, and the address is
                    // the only true thing about it.
                    name = row.name?.takeIf { it.isNotBlank() } ?: row.email.substringBefore('@'),
                    isActive = row.isActive,
                    boundToAccount = row.userId != null,
                    roles = row.roles.map { it.roleSlug }.sorted(),
                )
            }
        }

    override suspend fun addStaff(email: String, name: String): Either<WebError, Unit> =
        postgrest.insert(
            table = "admin_users",
            // Lower-cased here as well as checked by the table. The constraint would
            // reject a capital, and what a browser shows for a violated check is not
            // something the person who typed it can act on.
            body = """{"email":"${email.trim().lowercase().jsonEscaped()}",""" +
                """"name":"${name.trim().jsonEscaped()}"}""",
        )

    override suspend fun setStaffActive(id: String, active: Boolean): Either<WebError, Unit> =
        postgrest.patch(
            table = "admin_users",
            query = "id=eq.$id",
            body = """{"is_active":$active}""",
        )

    override suspend fun setStaffRole(
        adminId: String,
        roleSlug: String,
        held: Boolean,
    ): Either<WebError, Unit> = if (held) {
        postgrest.insert(
            table = "admin_user_roles",
            body = """{"admin_id":"$adminId","role_slug":"${roleSlug.jsonEscaped()}"}""",
            conflictIsFine = true,
        )
    } else {
        postgrest.delete(
            table = "admin_user_roles",
            query = "admin_id=eq.$adminId&role_slug=eq.${roleSlug.encoded()}",
        )
    }
}

@Serializable
private data class RoleRow(val slug: String, val name: String, val description: String = "")

@Serializable
private data class GrantRow(
    @SerialName("role_slug") val roleSlug: String,
    val permission: String,
)

@Serializable
private data class StaffRow(
    val id: String,
    val email: String,
    val name: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("admin_user_roles") val roles: List<HolderRow> = emptyList(),
)

@Serializable
private data class HolderRow(@SerialName("role_slug") val roleSlug: String)
