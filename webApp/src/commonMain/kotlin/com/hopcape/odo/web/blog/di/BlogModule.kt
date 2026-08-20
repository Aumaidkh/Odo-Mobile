package com.hopcape.odo.web.blog.di

import com.hopcape.odo.web.blog.data.SampleAdminRepository
import com.hopcape.odo.web.blog.data.SampleBlogRepository
import com.hopcape.odo.web.blog.infrastructure.FirebaseAuthRepository
import com.hopcape.odo.web.blog.platform.tokenStore
import io.ktor.client.HttpClient
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.presentation.ChromeViewModel
import com.hopcape.odo.web.blog.presentation.admin.SessionViewModel
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorViewModel
import com.hopcape.odo.web.blog.presentation.admin.library.AnalyticsViewModel
import com.hopcape.odo.web.blog.presentation.admin.library.MediaViewModel
import com.hopcape.odo.web.blog.presentation.admin.posts.PostsViewModel
import com.hopcape.odo.web.blog.presentation.admin.signin.SignInViewModel
import com.hopcape.odo.web.blog.presentation.article.ArticleViewModel
import com.hopcape.odo.web.blog.presentation.author.AuthorViewModel
import com.hopcape.odo.web.blog.presentation.category.CategoryViewModel
import com.hopcape.odo.web.blog.presentation.index.IndexViewModel
import com.hopcape.odo.web.blog.presentation.notfound.NotFoundViewModel
import com.hopcape.odo.web.blog.presentation.search.SearchViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Where the sample data is chosen, and the only file that changes when it stops
 * being sample data.
 *
 * The `single` lines below name implementations; everything above them asks for
 * the interface. Pointing the two sample ones at Supabase-backed classes is the
 * whole remaining integration on this side — no ViewModel, no screen and no test
 * has to move. Auth has already made that trip: the screens did not change when
 * it stopped being sample data.
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

    single<BlogRepository> { SampleBlogRepository() }
    single<AdminRepository> { SampleAdminRepository(auth = get()) }

    // Auth is the one thing here that is already real. The engine is not named:
    // ktor-client-js is the only one on this module's classpath, so `HttpClient`
    // finds it, and commonMain stays free of a browser type.
    single { HttpClient() }
    single<AuthRepository> { FirebaseAuthRepository(client = get(), tokens = tokenStore()) }

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
    // getOrNull, because a post being started has no id — and null is the state
    // the editor draws as "New post · not saved". The type argument is explicit
    // on purpose: inferred from the parameter it would be `String?`, and Koin
    // matches parameters by type, so a perfectly good slug would never be found
    // and every post would open as a blank new draft.
    viewModel { parameters ->
        EditorViewModel(postId = parameters.getOrNull<String>(), admin = get(), blog = get())
    }
}
