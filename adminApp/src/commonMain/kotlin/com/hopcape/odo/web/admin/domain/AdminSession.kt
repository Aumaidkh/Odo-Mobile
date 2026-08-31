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

    /** The initial the account chip draws. */
    val initial: String get() = name.take(1).uppercase().ifBlank { email.take(1).uppercase() }
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
