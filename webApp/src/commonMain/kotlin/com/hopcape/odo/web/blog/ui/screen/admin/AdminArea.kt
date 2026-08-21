package com.hopcape.odo.web.blog.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.hopcape.odo.web.blog.platform.copyToClipboard
import com.hopcape.odo.web.blog.platform.openExternal
import com.hopcape.odo.web.blog.presentation.CollectEffects
import com.hopcape.odo.web.blog.presentation.rememberRouteViewModel
import com.hopcape.odo.web.blog.presentation.admin.SessionViewModel
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorEffect
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorEvent
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorViewModel
import com.hopcape.odo.web.blog.presentation.admin.library.AnalyticsViewModel
import com.hopcape.odo.web.blog.presentation.admin.library.MediaViewModel
import com.hopcape.odo.web.blog.presentation.admin.posts.PostsViewModel
import com.hopcape.odo.web.blog.presentation.admin.settings.SettingsViewModel
import com.hopcape.odo.web.blog.presentation.admin.signin.SignInEffect
import com.hopcape.odo.web.blog.presentation.admin.signin.SignInViewModel
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.routing.BlogRoute.Admin
import com.hopcape.odo.web.blog.routing.Router
import com.hopcape.odo.web.blog.ui.chrome.AdminShell
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The CMS, behind its gate.
 *
 * The gate is a UI convenience, not the security: every call in
 * [com.hopcape.odo.web.blog.domain.AdminRepository] checks the session for
 * itself. What this stops is a signed-out author looking at an empty post table
 * and wondering why it is empty.
 *
 * [session] is resolved by the caller, in the page scope, so navigating between
 * CMS screens does not re-check and flash the login page in between.
 */
@Composable
fun AdminArea(
    route: Admin,
    session: SessionViewModel.State,
    onSignedIn: () -> Unit,
    onSignOut: () -> Unit,
    router: Router,
) {
    when (session) {
        // Nobody has been asked yet. Drawing either answer here would be a guess,
        // and both guesses are visible: one flashes the login page at somebody who
        // is signed in, the other shows the CMS to somebody who is not.
        SessionViewModel.State.Unknown ->
            Box(Modifier.fillMaxSize().background(BlogThemeTokens.colors.background))

        SessionViewModel.State.SignedOut -> SignInHost(route, onSignedIn, router)

        is SessionViewModel.State.SignedIn -> SignedInArea(route, session, onSignOut, router)
    }
}

/**
 * Sign in, wherever they were going.
 *
 * The URL is left alone. Somebody who followed a link to the editor and had to
 * sign in first lands on the editor, not on the post list — the address they
 * asked for is still the address they wanted.
 */
@Composable
private fun SignInHost(route: Admin, onSignedIn: () -> Unit, router: Router) {
    val viewModel: SignInViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SignInEffect.SignedIn -> {
                onSignedIn()
                // The sign-in page itself is not somewhere to stay once it has
                // worked; everything else already has the right URL.
                if (route is Admin.SignIn) router.replace(Admin.Posts)
            }
        }
    }

    SignInScreen(state, viewModel::onEvent)
}

/**
 * The CMS for somebody who is signed in.
 *
 * One `when`, no early returns. A composable that returns halfway emits a
 * different tree shape per route, and Compose keys its slots — including the
 * ViewModel each host resolves — off that shape. With the returns in place the
 * editor's host and the editor's own ViewModel ended up on different instances:
 * one loaded the post, the other was the one drawn, and the page stayed blank
 * with nothing failing anywhere.
 */
@Composable
private fun SignedInArea(
    route: Admin,
    session: SessionViewModel.State.SignedIn,
    onSignOut: () -> Unit,
    router: Router,
) {
    when (route) {
        // Already signed in, so the login URL has nothing to show. `replace`, not
        // `go`: it should not become a back-button stop.
        Admin.SignIn -> {
            LaunchedEffect(Unit) { router.replace(Admin.Posts) }
            Box(Modifier.fillMaxSize().background(BlogThemeTokens.colors.background))
        }

        // The editor is deliberately outside AdminShell — see EditorScreen.
        is Admin.Editor -> EditorHost(route, router)

        else -> AdminShell(
            current = route,
            session = session.session,
            onNavigate = router::go,
            onSignOut = onSignOut,
        ) {
            when (route) {
                Admin.Posts -> {
                    val viewModel: PostsViewModel = koinViewModel()
                    val state by viewModel.state.collectAsState()
                    PostsScreen(state, viewModel::onEvent, router::go)
                }

                Admin.Media -> {
                    val viewModel: MediaViewModel = koinViewModel()
                    val items by viewModel.items.collectAsState()
                    val uploading by viewModel.uploading.collectAsState()
                    MediaScreen(items, uploading, viewModel::onEvent)
                }

                Admin.Analytics -> {
                    val viewModel: AnalyticsViewModel = koinViewModel()
                    val state by viewModel.state.collectAsState()
                    AnalyticsScreen(state, viewModel::onEvent)
                }

                Admin.Settings -> {
                    val viewModel: SettingsViewModel = koinViewModel()
                    val state by viewModel.state.collectAsState()
                    SettingsScreen(state, viewModel::onEvent)
                }

                // Both handled by the outer `when`.
                Admin.SignIn, is Admin.Editor -> Unit
            }
        }
    }
}

@Composable
private fun EditorHost(route: Admin.Editor, router: Router) {
    val viewModel = rememberRouteViewModel<EditorViewModel>(key = route.postId ?: "new") {
        get { parametersOf(route.postId) }
    }
    val state by viewModel.state.collectAsState()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // Leaving is the editor's call, not the back button's: it is the only
            // thing that knows whether there is unsaved work to warn about.
            EditorEffect.Leave -> router.go(Admin.Posts)
            is EditorEffect.CopyUrl -> copyToClipboard(effect.url)
            is EditorEffect.OpenPost -> openExternal("/blog/${effect.slug}")
        }
    }

    EditorScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { viewModel.onEvent(EditorEvent.LeaveRequested) },
    )
}

/** Where a public route would go from the CMS. Kept for the editor's preview link. */
internal fun BlogRoute.isPublicPreview(): Boolean = this is BlogRoute.Public
