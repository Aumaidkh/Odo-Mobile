package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.hopcape.odo.web.admin.domain.AdminAuthRepository
import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.domain.Permission
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.firebase.FirebaseSignIn
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.SupabaseSession
import kotlinx.serialization.Serializable

/**
 * Signing in, end to end.
 *
 * Three steps, and each one is somebody else's job:
 *
 * 1. Firebase says the password is right and hands back a token.
 * 2. The `admin-session` edge function checks that token, checks the address is in
 *    `admin_users`, binds the account to that row, and mints a Supabase session
 *    with an `odo_admin` claim.
 * 3. `my_admin_identity()` says who that admin is and what they may do.
 *
 * After the first sign-in only the second and third matter: what survives a reload
 * is the Supabase refresh token, so coming back never touches Firebase. That is
 * why the Firebase token is never stored — it has one job and it is done in the
 * same second it is issued.
 *
 * **The staff check is deliberately not in this file.** It is in the function,
 * where a browser cannot reach it, and a 403 from step 2 is the only answer this
 * needs. Step 3 is not a second gate either: it is what the nav draws, and the
 * real permission check happens inside every RLS policy on every write.
 */
internal class SupabaseAdminAuthRepository(
    private val firebase: FirebaseSignIn,
    private val supabase: SupabaseSession,
    private val postgrest: Postgrest,
) : AdminAuthRepository {

    /**
     * Held so navigating between sections does not re-read the identity.
     * Cleared on sign-out, and rebuilt from the session on the next page load.
     */
    private var current: AdminSession? = null

    override suspend fun session(): Either<WebError, AdminSession?> = either {
        current?.let { return@either it }
        // No stored refresh token means signed out, which is not a failure.
        supabase.restore().bind() ?: return@either null
        val session = identity().bind()
        current = session
        session
    }

    override suspend fun signIn(email: String, password: String): Either<WebError, AdminSession> = either {
        val identity = firebase.identify(email, password).bind()
        supabase.exchange(identity.idToken).bind()
        val session = identity().bind()
        current = session
        session
    }

    override suspend fun signOut(): Either<WebError, Unit> {
        current = null
        supabase.clear()
        return Unit.right()
    }

    /**
     * The `admin_users` row behind the session, and its permissions.
     *
     * A null answer here is not "no permissions" — it is a session that belongs to
     * somebody who is not staff, which should not be possible after a successful
     * exchange but is exactly what a revoked admin looks like on a page reloaded
     * from a stored refresh token. It reads as [WebError.NotPermitted] rather than
     * as an empty panel, so the screen can say why.
     */
    private suspend fun identity(): Either<WebError, AdminSession> = either {
        val row = postgrest.rpcOne(
            name = "my_admin_identity",
            body = "{}",
            serializer = AdminIdentityRow.serializer(),
        ).bind() ?: raise(WebError.NotPermitted)

        supabase.subjectId = row.id
        AdminSession(
            id = row.id,
            email = row.email,
            name = row.name ?: row.email.substringBefore('@'),
            roles = row.roles,
            // Unknown strings are dropped rather than failing the sign-in. The
            // database may grow a permission before this build has a screen for
            // it, and refusing to sign in over one is the worse answer.
            permissions = row.permissions.mapNotNull(Permission::ofId).toSet(),
        )
    }
}

/** `my_admin_identity()`'s jsonb, as this client reads it. */
@Serializable
private data class AdminIdentityRow(
    val id: String,
    val email: String,
    val name: String? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)
