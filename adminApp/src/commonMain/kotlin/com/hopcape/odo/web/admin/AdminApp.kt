package com.hopcape.odo.web.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.presentation.SessionViewModel
import com.hopcape.odo.web.admin.presentation.cities.CitiesViewModel
import com.hopcape.odo.web.admin.presentation.audit.AuditViewModel
import com.hopcape.odo.web.admin.presentation.users.UsersViewModel
import com.hopcape.odo.web.admin.presentation.vehicles.VehiclesViewModel
import com.hopcape.odo.web.admin.presentation.signin.SignInEffect
import com.hopcape.odo.web.admin.presentation.signin.SignInViewModel
import com.hopcape.odo.web.admin.routing.AdminRoute
import com.hopcape.odo.web.admin.routing.Router
import com.hopcape.odo.web.admin.routing.landingFor
import com.hopcape.odo.web.admin.routing.mayOpen
import com.hopcape.odo.web.admin.ui.chrome.AdminShell
import com.hopcape.odo.web.admin.ui.screen.CitiesScreen
import com.hopcape.odo.web.admin.ui.screen.NoAccessScreen
import com.hopcape.odo.web.admin.ui.screen.AuditScreen
import com.hopcape.odo.web.admin.ui.screen.UsersScreen
import com.hopcape.odo.web.admin.ui.screen.VehiclesScreen
import com.hopcape.odo.web.admin.ui.screen.NoRolesScreen
import com.hopcape.odo.web.admin.ui.screen.NotFoundScreen
import com.hopcape.odo.web.admin.ui.screen.PlaceholderScreen
import com.hopcape.odo.web.admin.ui.screen.SignInScreen
import com.hopcape.odo.web.admin.ui.theme.AdminTheme
import com.hopcape.odo.web.core.presentation.CollectEffects
import com.hopcape.odo.web.core.presentation.RouteScope
import org.koin.compose.viewmodel.koinViewModel

/**
 * The shell: work out whether anybody is signed in, then draw accordingly.
 *
 * The gate is a convenience, not the security. Every table this panel touches is
 * behind an RLS policy that calls `admin_has()`, so a signed-out browser that
 * skipped this would see empty lists and failed writes rather than data. What the
 * gate stops is somebody staring at an empty table wondering why.
 *
 * The session is resolved once, in the page scope, so moving between sections does
 * not re-check and flash the sign-in page in between.
 */
@Composable
fun AdminApp(router: Router) {
    AdminTheme {
        RouteScope(key = "page") {
            val sessions: SessionViewModel = koinViewModel()
            val session by sessions.state.collectAsState()

            when (val state = session) {
                // Nobody has been asked yet. Drawing either answer here would be a
                // guess, and both guesses are visible: one flashes the sign-in page
                // at somebody who is signed in, the other shows the panel to
                // somebody who is not.
                SessionViewModel.State.Unknown ->
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

                SessionViewModel.State.SignedOut -> SignInHost(sessions::refresh)

                is SessionViewModel.State.SignedIn ->
                    SignedInArea(state.session, router, sessions::signOut)
            }
        }
    }
}

@Composable
private fun SignInHost(onSignedIn: () -> Unit) {
    val viewModel: SignInViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // Only re-read the session. Where to go next is not this screen's
            // call: the answer depends on which sections the new session may
            // open, and [SignedInArea] is what knows. Naming a route here sent a
            // support admin — who holds `users.read` and nothing else — straight
            // to the vehicle catalog and a "no access" page.
            SignInEffect.SignedIn -> onSignedIn()
        }
    }

    SignInScreen(state, viewModel::onEvent)
}

/**
 * Everything behind the gate.
 *
 * One `when`, no early returns. A composable that returns halfway emits a
 * different tree shape per route, and Compose keys its slots — including any
 * ViewModel a host resolves — off that shape. `:webApp` learned this the hard way:
 * its editor host and its editor ViewModel ended up as different instances, one
 * holding the data and the other being the one drawn, with nothing failing
 * anywhere.
 *
 * The permission check here is the second of three for the same fact. The rail
 * already hid what this role cannot open; this refuses it when the URL is typed
 * anyway; and RLS refuses the data underneath regardless. Only the last one is
 * load-bearing — the other two exist so nobody is left staring at an empty table
 * wondering whether it is broken or forbidden.
 */
@Composable
private fun SignedInArea(
    session: AdminSession,
    router: Router,
    onSignOut: () -> Unit,
) {
    when (val route = router.current) {
        // Already signed in, so the sign-in URL has nothing to show. `replace`,
        // not `go`: it should not become a back-button stop.
        AdminRoute.SignIn -> {
            val landing = landingFor(session)
            LaunchedEffect(landing) { landing?.let(router::replace) }
            // Nothing to land on is an account that is staff and holds no roles.
            // Saying so beats a blank page while a redirect that cannot happen
            // never fires.
            AdminShell(session, route, router::go, onSignOut) {
                if (landing == null) NoRolesScreen() else Blank()
            }
        }

        else -> AdminShell(session, route, router::go, onSignOut) {
            when {
                route is AdminRoute.NotFound -> NotFoundScreen()
                !session.mayOpen(route) -> NoAccessScreen()
                // One host per built section; everything else is still a
                // placeholder with a route, a permission and a nav item.
                route is AdminRoute.Cities -> CitiesHost()
                route is AdminRoute.Vehicles -> VehiclesHost()
                route is AdminRoute.Users -> UsersHost()
                route is AdminRoute.Audit -> AuditHost()
                else -> PlaceholderScreen(route)
            }
        }
    }
}

/** Nothing, in the page's own colour. For the frame before a redirect lands. */
@Composable
private fun Blank() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

/**
 * The cities catalog.
 *
 * Its own composable so the ViewModel is resolved inside the shell's content
 * slot, which is where its lifetime should be: leaving the section disposes it,
 * and coming back re-reads rather than showing a list that may be minutes stale.
 */
@Composable
private fun CitiesHost() {
    val viewModel: CitiesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    CitiesScreen(state, viewModel::onEvent)
}

/** The vehicle catalog. Scoped like the cities host, and for the same reason. */
@Composable
private fun VehiclesHost() {
    val viewModel: VehiclesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    VehiclesScreen(state, viewModel::onEvent)
}

/** Support's one-account lookup. */
@Composable
private fun UsersHost() {
    val viewModel: UsersViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    UsersScreen(state, viewModel::onEvent)
}

/** Who changed what. Read-only, because the table has no other kind of policy. */
@Composable
private fun AuditHost() {
    val viewModel: AuditViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    AuditScreen(state, viewModel::onEvent)
}
