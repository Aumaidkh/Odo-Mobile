package com.hopcape.odo.web.blog.infrastructure

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.blog.domain.model.Session
import com.hopcape.odo.web.core.infrastructure.firebase.FirebaseSignIn
import com.hopcape.odo.web.blog.infrastructure.supabase.AuthorRow
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.SupabaseSession
import com.hopcape.odo.web.core.infrastructure.supabase.encoded

/**
 * Signing in, end to end.
 *
 * Three steps, and each one is somebody else's job:
 *
 * 1. Firebase says the password is right and hands back a token.
 * 2. The `blog-session` edge function checks that token, checks the address is on
 *    the author list, and mints a Supabase session with a `blog_author` claim.
 * 3. Postgres says who that author is — the name and the slug on every byline.
 *
 * After the first sign-in only the second and third matter: what survives a
 * reload is the Supabase refresh token, so coming back does not touch Firebase at
 * all. That is why the Firebase token is never stored — it has one job and it is
 * done in the same second it is issued.
 *
 * The author check is deliberately not in this file. It is in the function, and
 * a 403 from step 2 is the only answer this needs.
 */
internal class BlogAuthRepository(
    private val firebase: FirebaseSignIn,
    private val supabase: SupabaseSession,
    private val postgrest: Postgrest,
) : AuthRepository {

    /**
     * Held so a navigation between CMS screens does not re-read the author row.
     * Cleared on sign-out, and rebuilt from the session on the next page load.
     */
    private var current: Session? = null

    override suspend fun session(): Either<WebError, Session?> = either {
        current?.let { return@either it }
        // No stored refresh token means signed out, which is not a failure.
        supabase.restore().bind() ?: return@either null
        val session = authorSession().bind()
        current = session
        session
    }

    override suspend fun signIn(email: String, password: String): Either<WebError, Session> = either {
        val identity = firebase.identify(email, password).bind()
        supabase.exchange(identity.idToken).bind()
        val session = authorSession(fallbackName = identity.displayName.ifBlank { identity.email }).bind()
        current = session
        session
    }

    override suspend fun signOut(): Either<WebError, Unit> {
        current = null
        supabase.clear()
        return Unit.right()
    }

    /**
     * The author row behind the session.
     *
     * `blog-session` creates one on first sign-in, so this should always find a
     * row. When it does not — a row deleted by hand, a race on first sign-in — the
     * CMS still opens under a name taken from the account, because being unable to
     * draw a byline is not a reason to lock somebody out of their own drafts.
     */
    private suspend fun authorSession(fallbackName: String = ""): Either<WebError, Session> = either {
        val email = supabase.email().orEmpty()
        val row = postgrest.select(
            table = "blog_authors",
            serializer = AuthorRow.serializer(),
            query = "email=eq.${email.encoded()}&limit=1",
        ).bind().firstOrNull()

        supabase.subjectId = row?.id
        val name = row?.name ?: fallbackName.ifBlank { email.substringBefore('@') }
        Session(
            authorSlug = row?.slug ?: name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
            name = name,
            initial = (row?.initial ?: name.take(1)).uppercase().ifBlank { "?" },
        )
    }
}
