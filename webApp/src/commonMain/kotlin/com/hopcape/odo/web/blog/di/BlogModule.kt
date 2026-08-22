package com.hopcape.odo.web.blog.di

import com.hopcape.odo.web.blog.data.SampleAdminRepository
import com.hopcape.odo.web.blog.data.SampleAuthRepository
import com.hopcape.odo.web.blog.data.SampleBlogRepository
import com.hopcape.odo.web.blog.infrastructure.BlogAuthRepository
import com.hopcape.odo.web.blog.infrastructure.firebase.FirebaseSignIn
import com.hopcape.odo.web.blog.infrastructure.supabase.BuildBlogConfig
import com.hopcape.odo.web.blog.infrastructure.supabase.JsonPostImporter
import com.hopcape.odo.web.blog.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.blog.infrastructure.supabase.SupabaseAdminRepository
import com.hopcape.odo.web.blog.infrastructure.supabase.SupabaseBlogRepository
import com.hopcape.odo.web.blog.infrastructure.supabase.SupabaseSession
import com.hopcape.odo.web.blog.platform.tokenStore
import io.ktor.client.HttpClient
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.PostImporter
import com.hopcape.odo.web.blog.presentation.ChromeViewModel
import com.hopcape.odo.web.blog.presentation.admin.SessionViewModel
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorViewModel
import com.hopcape.odo.web.blog.presentation.admin.library.AnalyticsViewModel
import com.hopcape.odo.web.blog.presentation.admin.library.MediaViewModel
import com.hopcape.odo.web.blog.presentation.admin.posts.PostsViewModel
import com.hopcape.odo.web.blog.presentation.admin.settings.SettingsViewModel
import com.hopcape.odo.web.blog.presentation.admin.signin.SignInViewModel
import com.hopcape.odo.web.blog.presentation.article.ArticleViewModel
import com.hopcape.odo.web.blog.presentation.author.AuthorViewModel
import com.hopcape.odo.web.blog.presentation.category.CategoryViewModel
import com.hopcape.odo.web.blog.presentation.index.IndexViewModel
import com.hopcape.odo.web.blog.presentation.notfound.NotFoundViewModel
import com.hopcape.odo.web.blog.presentation.search.SearchViewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Declared above `blogModule`, and that is not a style choice.
 *
 * `module { }` runs its block immediately to build the definition list, so
 * anything it reads has to already exist. Below the module, these are still null
 * when the definitions register — the qualifier silently becomes "none", both
 * clients land on the same key, and the first screen to ask for one fails with a
 * message naming the thing that asked rather than the thing that was missing.
 */

/** Reads as a stranger. Never sends a session token, even when there is one. */
private val ANONYMOUS = named("anonymous")

/** Reads and writes as the signed-in author. */
private val AS_AUTHOR = named("author")

/**
 * Where the sample data is chosen, and the only file that changes when it stops
 * being sample data.
 *
 * The `single` lines below name implementations; everything above them asks for
 * the interface. All three now have a real one behind them, and the switch is a
 * single condition: credentials present, or not. No ViewModel, no screen and no
 * test moved when they arrived.
 *
 * The repositories are singletons rather than factories because two of them hold
 * state that has to outlive a route: the session, and the edits made to a draft.
 * A factory would hand the editor a fresh, empty store on every navigation.
 *
 * Route arguments arrive as constructor parameters, not through a saved-state
 * handle — the same rule the app's features follow, and here for a plainer
 * reason: the argument is in the URL, and the URL is what built the screen.
 */
val blogModule: Module = module {

    single { HttpClient() }

    // The wire format is the database's, so reading it is the database layer's
    // job even when the paste never goes near a network.
    single<PostImporter> { JsonPostImporter() }

    /**
     * Whether this checkout has a database to talk to.
     *
     * A clone with no `local.properties` still has to build and run — the sample
     * repositories are what it gets, the same way the app keeps its fakes. Real
     * credentials swap all three implementations at once; there is no state where
     * half the blog is live.
     */
    single {
        BlogBackend(
            isLive = BuildBlogConfig.SUPABASE_URL.isNotBlank() && BuildBlogConfig.SUPABASE_ANON_KEY.isNotBlank(),
        )
    }

    single {
        SupabaseSession(
            client = get(),
            baseUrl = BuildBlogConfig.SUPABASE_URL,
            anonKey = BuildBlogConfig.SUPABASE_ANON_KEY,
            tokens = tokenStore(),
        )
    }

    /**
     * Two clients, and the difference matters.
     *
     * The public one never sends a session token. With one shared client, an
     * author who signed in and then browsed the blog was reading it as
     * `authenticated` — and the author policy let them see their own drafts on
     * the public pages. The reader-facing side has no business knowing whether
     * anybody is signed in.
     */
    single(ANONYMOUS) {
        Postgrest(
            client = get(),
            baseUrl = BuildBlogConfig.SUPABASE_URL,
            anonKey = BuildBlogConfig.SUPABASE_ANON_KEY,
            accessToken = { null },
        )
    }

    single(AS_AUTHOR) {
        Postgrest(
            client = get(),
            baseUrl = BuildBlogConfig.SUPABASE_URL,
            anonKey = BuildBlogConfig.SUPABASE_ANON_KEY,
            accessToken = { get<SupabaseSession>().accessToken() },
        )
    }

    single<BlogRepository> {
        if (get<BlogBackend>().isLive) {
            SupabaseBlogRepository(postgrest = get(ANONYMOUS))
        } else {
            SampleBlogRepository()
        }
    }

    single<AuthRepository> {
        if (get<BlogBackend>().isLive) {
            BlogAuthRepository(
                firebase = FirebaseSignIn(client = get(), apiKey = FIREBASE_WEB_API_KEY),
                supabase = get(),
                postgrest = get(AS_AUTHOR),
            )
        } else {
            SampleAuthRepository()
        }
    }

    single<AdminRepository> {
        if (get<BlogBackend>().isLive) {
            SupabaseAdminRepository(
                postgrest = get(AS_AUTHOR),
                client = get(),
                baseUrl = BuildBlogConfig.SUPABASE_URL,
                anonKey = BuildBlogConfig.SUPABASE_ANON_KEY,
                accessToken = { get<SupabaseSession>().accessToken() },
                authorId = { get<SupabaseSession>().authorId },
            )
        } else {
            SampleAdminRepository(auth = get())
        }
    }

    // Chrome — the nav categories, read once for the whole page rather than by
    // each screen that draws a header.
    viewModel { ChromeViewModel(blog = get()) }

    viewModel { IndexViewModel(blog = get()) }
    viewModel { parameters -> ArticleViewModel(slug = parameters.get(), blog = get()) }
    viewModel { parameters -> CategoryViewModel(slug = parameters.get(), blog = get()) }
    viewModel { parameters -> AuthorViewModel(slug = parameters.get(), blog = get()) }
    viewModel { parameters -> SearchViewModel(query = parameters.get(), blog = get()) }
    viewModel { NotFoundViewModel(blog = get()) }

    // Admin. SessionViewModel is resolved in the page scope, not the route scope,
    // so navigating between CMS screens does not re-check who is signed in and
    // flash the login page in between.
    viewModel { SessionViewModel(auth = get()) }
    viewModel { SignInViewModel(auth = get()) }
    viewModel { PostsViewModel(admin = get()) }
    viewModel { MediaViewModel(admin = get()) }
    viewModel { AnalyticsViewModel(admin = get()) }
    viewModel { SettingsViewModel(admin = get()) }
    // getOrNull, because a post being started has no id — and null is the state
    // the editor draws as "New post · not saved". The type argument is explicit
    // on purpose: inferred from the parameter it would be `String?`, and Koin
    // matches parameters by type, so a perfectly good slug would never be found
    // and every post would open as a blank new draft.
    viewModel { parameters ->
        EditorViewModel(
            postId = parameters.getOrNull<String>(),
            admin = get(),
            blog = get(),
            importer = get(),
        )
    }
}


/**
 * The Firebase web API key.
 *
 * A public client identifier — the same class of value the app ships inside
 * `google-services.json` and `web/build.ts` bakes into the account-deletion page.
 * It names the project; it authorises nothing. What decides who may publish is
 * `BLOG_AUTHOR_EMAILS` in the edge function's environment.
 */
/**
 * Whether there is a database behind this build.
 *
 * A type rather than a bare `Boolean`, because Koin resolves by type and a second
 * boolean binding anywhere in the graph would silently answer this question.
 * Overriding it is also how a test asks for the sample repositories.
 */
data class BlogBackend(val isLive: Boolean)


private const val FIREBASE_WEB_API_KEY = "AIzaSyB8A39cTEw-_4mtRntVatyf5ZWYhiwojUc"
