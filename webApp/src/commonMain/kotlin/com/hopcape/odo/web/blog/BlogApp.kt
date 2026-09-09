package com.hopcape.odo.web.blog

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.hopcape.odo.web.blog.presentation.ChromeViewModel
import com.hopcape.odo.web.blog.presentation.admin.SessionViewModel
import com.hopcape.odo.web.core.presentation.RouteScope
import com.hopcape.odo.web.core.presentation.state.isMissing
import com.hopcape.odo.web.blog.presentation.article.ArticleViewModel
import com.hopcape.odo.web.blog.presentation.author.AuthorViewModel
import com.hopcape.odo.web.blog.presentation.category.CategoryViewModel
import com.hopcape.odo.web.blog.presentation.index.IndexViewModel
import com.hopcape.odo.web.blog.presentation.notfound.NotFoundViewModel
import com.hopcape.odo.web.blog.presentation.search.SearchViewModel
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.routing.BlogRoute.Admin
import com.hopcape.odo.web.blog.routing.BlogRoute.Public
import com.hopcape.odo.web.blog.routing.Router
import com.hopcape.odo.web.blog.routing.location
import com.hopcape.odo.web.blog.ui.chrome.PublicShell
import com.hopcape.odo.web.blog.ui.screen.ArticleScreen
import com.hopcape.odo.web.blog.ui.screen.AuthorScreen
import com.hopcape.odo.web.blog.ui.screen.CategoryScreen
import com.hopcape.odo.web.blog.ui.screen.IndexScreen
import com.hopcape.odo.web.blog.ui.screen.NotFoundScreen
import com.hopcape.odo.web.blog.ui.screen.SearchScreen
import com.hopcape.odo.web.blog.ui.screen.admin.AdminArea
import com.hopcape.odo.web.blog.ui.theme.AdminColors
import com.hopcape.odo.web.blog.ui.theme.BlogTheme
import com.hopcape.odo.web.blog.ui.theme.COMPACT_WIDTH_DP
import com.hopcape.odo.web.blog.ui.theme.PublicColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The shell: pick a skin, scope the ViewModels, draw the page.
 *
 * Three things happen here and nowhere else.
 *
 * The width is measured once, at the top, and published as
 * [com.hopcape.odo.web.blog.ui.theme.LocalCompact]. The design has separate
 * desktop and phone frames whose differences are structural — a contents rail
 * becomes a strip, a two-column grid becomes one — so screens branch on a
 * boolean rather than every one of them measuring for itself.
 *
 * The skin is chosen from which half of the route tree is showing. Public is
 * black and Admin is white, and neither follows the system preference: they are
 * two designs, not two modes of one.
 *
 * And the ViewModels are scoped twice. The outer scope lives as long as the page
 * and holds the nav; the inner one is keyed to the route's own location, so
 * opening a second article builds a second ViewModel instead of handing back the
 * first one still loaded with the wrong post.
 */
@Composable
fun BlogApp(router: Router) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < COMPACT_WIDTH_DP.dp
        val route = router.current

        BlogTheme(
            colors = if (route is Admin) AdminColors else PublicColors,
            compact = compact,
        ) {
            RouteScope(key = "page") {
                val chrome: ChromeViewModel = koinViewModel()
                val categories by chrome.categories.collectAsState()
                // Page-scoped for the same reason the nav is: the CMS asks who is
                // signed in once per page load, not once per screen.
                val sessions: SessionViewModel = koinViewModel()
                val session by sessions.state.collectAsState()

                RouteScope(key = route.location()) {
                    when (route) {
                        is Public -> PublicShell(
                            categories = categories,
                            activeCategorySlug = (route as? Public.Category)?.slug,
                            searchTerm = (route as? Public.Search)?.query.orEmpty(),
                            onNavigate = router::go,
                        ) {
                            PublicPage(route, router)
                        }

                        is Admin -> AdminArea(
                            route = route,
                            session = session,
                            onSignedIn = sessions::refresh,
                            onSignOut = {
                                sessions.signOut()
                                router.replace(Admin.SignIn)
                            },
                            router = router,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One public page.
 *
 * Every branch is the same three lines — get the ViewModel, collect its state,
 * draw the screen — which is what a route host should be. Anything more than
 * that in here would be logic a ViewModel should own.
 */
@Composable
private fun PublicPage(route: Public, router: Router) {
    when (route) {
        Public.Index -> {
            val viewModel: IndexViewModel = koinViewModel()
            val state by viewModel.state.collectAsState()
            IndexScreen(state, viewModel::onEvent, router::go)
        }

        // A slug that matches nothing is a reader on a dead link, and the design
        // has a page for that. Without this they would get one line of error text
        // on an otherwise empty page — technically a 404, practically an exit.
        is Public.Article -> {
            val viewModel: ArticleViewModel = koinViewModel { parametersOf(route.slug) }
            val state by viewModel.state.collectAsState()
            if (state.article.isMissing) NotFoundHost(router) else ArticleScreen(state, viewModel::onEvent, router::go)
        }

        is Public.Category -> {
            val viewModel: CategoryViewModel = koinViewModel { parametersOf(route.slug) }
            val state by viewModel.state.collectAsState()
            if (state.page.isMissing) NotFoundHost(router) else CategoryScreen(state, viewModel::onEvent, router::go)
        }

        is Public.Author -> {
            val viewModel: AuthorViewModel = koinViewModel { parametersOf(route.slug) }
            val state by viewModel.state.collectAsState()
            if (state.isMissing) NotFoundHost(router) else AuthorScreen(state, viewModel::onEvent, router::go)
        }

        is Public.Search -> {
            val viewModel: SearchViewModel = koinViewModel { parametersOf(route.query) }
            val state by viewModel.state.collectAsState()
            SearchScreen(state, viewModel::onEvent, router::go)
        }

        is Public.NotFound -> NotFoundHost(router)
    }
}

/**
 * The 404 page, reachable four ways.
 *
 * A path that matches no route at all, and a slug that matches no post, category
 * or author. All four land here rather than on their own version of "not found",
 * because a reader cannot tell the difference and should not have to.
 */
@Composable
private fun NotFoundHost(router: Router) {
    val viewModel: NotFoundViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    NotFoundScreen(state, viewModel::onEvent, router::go)
}
