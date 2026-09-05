package com.hopcape.odo.web.admin.di

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.admin.domain.AdminAuthRepository
import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.domain.CitiesRepository
import com.hopcape.odo.web.admin.domain.AuditRepository
import com.hopcape.odo.web.admin.domain.BillingRepository
import com.hopcape.odo.web.admin.domain.CatalogueRepository
import com.hopcape.odo.web.admin.domain.ContentRepository
import com.hopcape.odo.web.admin.domain.TicketsRepository
import com.hopcape.odo.web.admin.domain.FlagsRepository
import com.hopcape.odo.web.admin.domain.SocialRepository
import com.hopcape.odo.web.admin.domain.RolesRepository
import com.hopcape.odo.web.admin.domain.DashboardRepository
import com.hopcape.odo.web.admin.domain.UsersRepository
import com.hopcape.odo.web.admin.domain.VehiclesRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseAdminAuthRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseCitiesRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseAuditRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseBillingRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseCatalogueRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseContentRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseTicketsRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseFlagsRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseSocialRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseInvites
import com.hopcape.odo.web.admin.infrastructure.SupabaseRolesRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseDashboardRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseUsersRepository
import com.hopcape.odo.web.admin.infrastructure.SupabaseVehiclesRepository
import com.hopcape.odo.web.admin.presentation.SessionViewModel
import com.hopcape.odo.web.admin.presentation.cities.CitiesViewModel
import com.hopcape.odo.web.admin.presentation.audit.AuditViewModel
import com.hopcape.odo.web.admin.presentation.dashboard.DashboardViewModel
import com.hopcape.odo.web.admin.presentation.catalogue.BillingViewModel
import com.hopcape.odo.web.admin.presentation.catalogue.CatalogueViewModel
import com.hopcape.odo.web.admin.presentation.catalogue.TicketsViewModel
import com.hopcape.odo.web.admin.presentation.content.ContentViewModel
import com.hopcape.odo.web.admin.presentation.content.PostDetailViewModel
import com.hopcape.odo.web.admin.presentation.flags.FlagsViewModel
import com.hopcape.odo.web.admin.presentation.social.SocialViewModel
import com.hopcape.odo.web.admin.presentation.roles.RolesViewModel
import com.hopcape.odo.web.admin.presentation.users.UsersViewModel
import com.hopcape.odo.web.admin.presentation.vehicles.VehiclesViewModel
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
            // Nothing in this panel is meant to be read anonymously, and an
            // anonymous read here does not fail — RLS answers it `200 []`, which
            // every screen draws as an empty table. Refusing outright turns a
            // missing session back into an error somebody can act on.
            requireSession = true,
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

    // No `isConfigured` branch here. Without credentials nobody gets a session,
    // so nothing ever resolves this — and a sample catalog would only make an
    // unconfigured build look like it was working.
    single<CitiesRepository> { SupabaseCitiesRepository(postgrest = get()) }
    single<VehiclesRepository> { SupabaseVehiclesRepository(postgrest = get()) }
    single<DashboardRepository> { SupabaseDashboardRepository(postgrest = get()) }
    single<UsersRepository> { SupabaseUsersRepository(postgrest = get()) }
    single<AuditRepository> { SupabaseAuditRepository(postgrest = get()) }
    single {
        SupabaseInvites(
            client = get(),
            baseUrl = BuildWebConfig.SUPABASE_URL,
            anonKey = BuildWebConfig.SUPABASE_ANON_KEY,
            session = get(),
        )
    }
    single<RolesRepository> { SupabaseRolesRepository(postgrest = get(), invites = get()) }
    single<ContentRepository> { SupabaseContentRepository(postgrest = get()) }
    single<CatalogueRepository> { SupabaseCatalogueRepository(postgrest = get()) }
    single<TicketsRepository> { SupabaseTicketsRepository(postgrest = get()) }
    single<BillingRepository> { SupabaseBillingRepository(postgrest = get()) }
    single<FlagsRepository> { SupabaseFlagsRepository(postgrest = get()) }
    single<SocialRepository> {
        SupabaseSocialRepository(
            postgrest = get(),
            projectUrl = BuildWebConfig.SUPABASE_URL,
            projectKey = BuildWebConfig.SUPABASE_ANON_KEY,
        )
    }

    viewModel { DashboardViewModel(dashboard = get()) }
    viewModel { SessionViewModel(auth = get()) }
    viewModel { SignInViewModel(auth = get()) }
    viewModel { CitiesViewModel(cities = get()) }
    viewModel { VehiclesViewModel(vehicles = get()) }
    viewModel { UsersViewModel(users = get()) }
    viewModel { AuditViewModel(audit = get()) }
    viewModel { RolesViewModel(repository = get()) }
    viewModel { ContentViewModel(content = get()) }
    // Parameterised: the post id comes from the URL, so this is a factory rather
    // than a single — two posts opened in turn must be two loads.
    viewModel { (id: String) -> PostDetailViewModel(content = get(), postId = id) }
    viewModel { CatalogueViewModel(catalogue = get()) }
    viewModel { TicketsViewModel(tickets = get()) }
    viewModel { BillingViewModel(billing = get()) }
    viewModel { FlagsViewModel(flags = get()) }
    viewModel { SocialViewModel(social = get()) }
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
