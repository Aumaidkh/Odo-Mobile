package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/**
 * Who is signed in, and what the nav may show them.
 *
 * [id] is the `admin_users` row, not the `auth.users` account — it is what an
 * audit row is attributed to and what a "granted by" reads back as.
 *
 * [permissions] is a set of [Permission] rather than the raw strings, so a typo at
 * a call site is a compile error instead of a nav item that never appears. Strings
 * the database knows and this build does not are dropped on the way in.
 */
data class AdminSession(
    val id: String,
    val email: String,
    val name: String,
    val roles: List<String>,
    val permissions: Set<Permission>,
) {

    /**
     * Whether the nav should offer this.
     *
     * Never a reason to allow a write. The server decides that, on every request,
     * and this answering true for something RLS refuses is a bug in the nav rather
     * than a hole in the panel.
     */
    fun can(permission: Permission): Boolean = permission in permissions

    /**
     * The two letters the account chip draws.
     *
     * Two rather than one because a 28dp circle has room and one letter is a
     * weak identifier on a shared tool — "A" belongs to too many people.
     * Falls back to the address when there is no name yet.
     */
    val initials: String
        get() {
            val parts = name.trim().split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> email.take(2).uppercase()
            }
        }

    /**
     * The role, for the chip beside the name and in the header.
     *
     * The first one held, title-cased from its slug. Somebody with two roles is
     * rare and the chip has room for one; the full list is on the roles screen.
     */
    val roleLabel: String
        get() = roles.firstOrNull()
            ?.split("_")
            ?.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            ?: "No role"
}

/**
 * Signing in, and knowing whether you already are.
 *
 * Three calls, mirroring `:webApp`'s `AuthRepository`, because the shape is the
 * same one: a session that may or may not exist, a way to get one, and a way to
 * give it up.
 */
interface AdminAuthRepository {

    /** The current session, or null when signed out. Not an error either way. */
    suspend fun session(): Either<WebError, AdminSession?>

    /**
     * Firebase proves the password, `admin-session` decides whether that address
     * is staff, and `my_admin_identity()` says what they may do.
     *
     * [WebError.NotPermitted] is the address being right and not being staff — a
     * different thing from a wrong password, and it has to stay different or the
     * screen sends somebody to check a password that was never the problem.
     */
    suspend fun signIn(email: String, password: String): Either<WebError, AdminSession>

    suspend fun signOut(): Either<WebError, Unit>
}
