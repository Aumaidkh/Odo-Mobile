package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/** One role, and how many people hold it. */
data class AdminRole(
    val slug: String,
    val name: String,
    val description: String,
    val memberCount: Int,
)

/**
 * The roles, the permission grid, and who holds what.
 *
 * The grid is granted / not granted, two states rather than the design's
 * none/read/write/full. Four levels would be four words on a screen and one
 * boolean in the database — `admin_role_permissions` stores a permission or does
 * not, and `admin_has()` answers yes or no. Drawing a "READ" cell that the server
 * cannot distinguish from "WRITE" would be a UI making a promise nothing keeps.
 */
interface RolesRepository {

    suspend fun roles(): Either<WebError, List<AdminRole>>

    /** Every granted (role, permission) pair. Absent means not granted. */
    suspend fun grants(): Either<WebError, Set<Pair<String, String>>>

    suspend fun setGrant(roleSlug: String, permission: String, granted: Boolean): Either<WebError, Unit>

    /**
     * Adds a role.
     *
     * With no permissions. A role is created and then granted things in the grid,
     * rather than picked from a list of presets — the grid is already the place
     * permissions are decided, and a second place to decide them is a second place
     * to get them wrong.
     */
    suspend fun createRole(slug: String, name: String, description: String): Either<WebError, Unit>

    suspend fun staff(): Either<WebError, List<StaffMember>>

    /**
     * Puts an address on the allowlist.
     *
     * By email, and the row exists before the person does: `admin-session` refuses
     * anybody who is not here, and the first sign-in is what binds an account to the
     * row. So the order is add-then-invite, not invite-then-add.
     */
    suspend fun addStaff(email: String, name: String): Either<WebError, Unit>

    /**
     * Revokes or restores access.
     *
     * `is_active`, never a delete: the audit log points at these rows, and "who used
     * to have this" is a question worth being able to answer.
     */
    suspend fun setStaffActive(id: String, active: Boolean): Either<WebError, Unit>

    suspend fun setStaffRole(adminId: String, roleSlug: String, held: Boolean): Either<WebError, Unit>

    /**
     * Gets somebody into a state where they can actually sign in.
     *
     * Adding a row to the allowlist is only half of it: the password lives in a
     * Firebase account, and until one exists there is nothing to sign in with. This
     * creates that account and asks Firebase to email them a link to choose their
     * own password.
     *
     * Safe to call again. Re-inviting somebody who already has an account just
     * sends them a fresh link, which is what "I never got the email" needs.
     */
    suspend fun invite(email: String): Either<WebError, Unit>
}

/**
 * One person on the staff allowlist.
 *
 * [boundToAccount] is false until they have signed in once. Worth showing: an
 * address that has been added but never used looks identical to a working account
 * until somebody asks why their permissions do nothing.
 */
data class StaffMember(
    val id: String,
    val email: String,
    val name: String,
    val isActive: Boolean,
    val boundToAccount: Boolean,
    val roles: List<String>,
)
