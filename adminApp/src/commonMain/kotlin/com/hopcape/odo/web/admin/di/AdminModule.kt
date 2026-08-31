package com.hopcape.odo.web.admin.di

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.admin.domain.AdminAuthRepository
import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.infrastructure.SupabaseAdminAuthRepository
import com.hopcape.odo.web.admin.presentation.SessionViewModel
import com.hopcape.odo.web.admin.presentation.signin.SignInViewModel
import com.hopcape.odo.web.core.config.BuildWebConfig
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.firebase.FirebaseSignIn
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.SupabaseSession
import com.hopcape.odo.web.core.platform.tokenStore
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The graph.
 *
 * One client, one session, one PostgREST, and the repository over them. There is
 * no anonymous/authenticated split the way `:webApp` has: nothing in this panel is
 * ever read as a stranger, so a second client that deliberately drops the token
 * would have no caller.
 */
val adminModule: Module = module {

    single { HttpClient() }

    single { AdminBackend(isConfigured = BuildWebConfig.isConfigured) }

    single {
        SupabaseSession(
            client = get(),
            baseUrl = BuildWebConfig.SUPABASE_URL,
            anonKey = BuildWebConfig.SUPABASE_ANON_KEY,
            tokens = tokenStore(),
            // Not `blog-session`. That one checks an author list and stamps a
            // different claim; naming the function here is what keeps the two
            // gates from quietly becoming one.
            sessionFunction = "admin-session",
        )
    }

    single {
        Postgrest(
            client = get(),
            baseUrl = BuildWebConfig.SUPABASE_URL,
            anonKey = BuildWebConfig.SUPABASE_ANON_KEY,
            accessToken = { get<SupabaseSession>().accessToken() },
        )
    }

    single<AdminAuthRepository> {
        if (get<AdminBackend>().isConfigured) {
            SupabaseAdminAuthRepository(
                firebase = FirebaseSignIn(client = get(), apiKey = BuildWebConfig.FIREBASE_WEB_API_KEY),
                supabase = get(),
                postgrest = get(),
            )
        } else {
            UnconfiguredAuthRepository
        }
    }

    viewModel { SessionViewModel(auth = get()) }
    viewModel { SignInViewModel(auth = get()) }
}

/**
 * Whether this build has a backend behind it.
 *
 * A type rather than a bare `Boolean`, because Koin resolves by type and a second
 * boolean binding anywhere in the graph would silently answer this question.
 */
data class AdminBackend(val isConfigured: Boolean)

/**
 * What a build with no credentials signs in with: nothing, and it says so.
 *
 * This exists because of a trap this codebase has fallen into three times — a
 * build that is missing `local.properties` (a CI runner, a fresh clone) shipping
 * anyway and failing at the first request with a message about the request rather
 * than about the configuration. Sign-in is refused up front with the one error
 * whose copy names the actual cause.
 */
private object UnconfiguredAuthRepository : AdminAuthRepository {
    override suspend fun session(): Either<WebError, AdminSession?> = null.right()
    override suspend fun signIn(email: String, password: String): Either<WebError, AdminSession> =
        WebError.SignInUnavailable.left()
    override suspend fun signOut(): Either<WebError, Unit> = Unit.right()
}
