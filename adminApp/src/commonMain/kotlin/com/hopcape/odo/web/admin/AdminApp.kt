package com.hopcape.odo.web.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.presentation.SessionViewModel
import com.hopcape.odo.web.admin.presentation.audit.AuditEvent
import com.hopcape.odo.web.admin.presentation.audit.AuditViewModel
import com.hopcape.odo.web.admin.presentation.content.PostDetailViewModel
import org.koin.core.parameter.parametersOf
import com.hopcape.odo.web.admin.presentation.dashboard.DashboardViewModel
import com.hopcape.odo.web.admin.presentation.cities.CitiesEvent
import com.hopcape.odo.web.admin.presentation.cities.CitiesViewModel
import com.hopcape.odo.web.admin.presentation.catalogue.BillingEvent
import com.hopcape.odo.web.admin.presentation.catalogue.BillingViewModel
import com.hopcape.odo.web.admin.presentation.catalogue.CatalogueEvent
import com.hopcape.odo.web.admin.presentation.catalogue.CatalogueViewModel
import com.hopcape.odo.web.admin.presentation.catalogue.TicketsEvent
import com.hopcape.odo.web.admin.presentation.catalogue.TicketsViewModel
import com.hopcape.odo.web.admin.presentation.content.ContentEvent
import com.hopcape.odo.web.admin.presentation.content.ContentViewModel
import com.hopcape.odo.web.admin.presentation.flags.FlagsEvent
import com.hopcape.odo.web.admin.presentation.flags.FlagsViewModel
import com.hopcape.odo.web.admin.presentation.social.SocialViewModel
import com.hopcape.odo.web.admin.presentation.roles.RolesEvent
import com.hopcape.odo.web.admin.presentation.roles.RolesViewModel
import com.hopcape.odo.web.admin.presentation.signin.SignInEffect
import com.hopcape.odo.web.admin.presentation.signin.SignInViewModel
import com.hopcape.odo.web.admin.presentation.users.UsersEvent
import com.hopcape.odo.web.admin.presentation.users.UsersViewModel
import com.hopcape.odo.web.admin.presentation.vehicles.VehiclesEvent
import com.hopcape.odo.web.admin.presentation.vehicles.VehiclesViewModel
import com.hopcape.odo.web.admin.routing.AdminRoute
import com.hopcape.odo.web.admin.routing.Router
import com.hopcape.odo.web.admin.routing.landingFor
import com.hopcape.odo.web.admin.routing.mayOpen
import com.hopcape.odo.web.admin.ui.chrome.AdminShell
import com.hopcape.odo.web.admin.ui.screen.AuditScreen
import com.hopcape.odo.web.admin.ui.screen.CitiesScreen
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_shell_wordmark
import com.hopcape.odo.web.admin.ui.screen.BootScreen
import com.hopcape.odo.web.admin.ui.screen.DashboardScreen
import com.hopcape.odo.web.admin.ui.screen.PostDetailScreen
import com.hopcape.odo.web.admin.ui.screen.TicketDetailScreen
import com.hopcape.odo.web.admin.ui.screen.BillingScreen
import com.hopcape.odo.web.admin.ui.screen.CatalogueScreen
import com.hopcape.odo.web.admin.ui.screen.ContentScreen
import com.hopcape.odo.web.admin.ui.screen.TicketsScreen
import com.hopcape.odo.web.admin.ui.screen.FlagsScreen
import com.hopcape.odo.web.admin.ui.screen.SocialScreen
import com.hopcape.odo.web.admin.ui.screen.NoAccessScreen
import com.hopcape.odo.web.admin.ui.screen.NoRolesScreen
import com.hopcape.odo.web.admin.ui.screen.NotBuiltScreen
import com.hopcape.odo.web.admin.ui.screen.NotFoundScreen
import com.hopcape.odo.web.admin.ui.screen.RolesScreen
import com.hopcape.odo.web.admin.ui.screen.SignInScreen
import com.hopcape.odo.web.admin.ui.screen.UsersScreen
import com.hopcape.odo.web.admin.ui.screen.VehiclesScreen
import com.hopcape.odo.web.admin.ui.chromePreferences
import com.hopcape.odo.web.admin.ui.theme.AdminTheme
import org.jetbrains.compose.resources.getString
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
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
    // Read once. The store is localStorage, and re-reading it on every recomposition
    // would be a synchronous browser call per frame for a value that changes when
    // somebody flips a switch.
    val preferences = remember { chromePreferences() }
    // Dark unless somebody has said otherwise. Not the OS preference: this is a tool
    // whose look is chosen and remembered, and following the system setting would
    // overrule that choice on every launch.
    var dark by remember { mutableStateOf(preferences.darkTheme ?: true) }
    var collapsed by remember { mutableStateOf(preferences.railCollapsed) }

    AdminTheme(dark = dark) {
        // Nothing is drawn until the string table is in.
        //
        // Compose Resources loads the whole `.cvr` for a qualifier in one read, so
        // awaiting a single string warms every string in it. Awaiting one is the
        // difference between a panel that arrives complete and one that spends
        // forty seconds showing unlabelled icons and empty headings — which is what
        // production shipped, and what it was reported as broken for.
        var stringsReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            getString(Res.string.ad_shell_wordmark)
            stringsReady = true
        }

        if (!stringsReady) {
            BootScreen()
            return@AdminTheme
        }

        RouteScope(key = "page") {
            val sessions: SessionViewModel = koinViewModel()
            val session by sessions.state.collectAsState()

            when (val state = session) {
                // Nobody has been asked yet. Drawing either answer here would be a
                // guess, and both guesses are visible: one flashes the sign-in page
                // at somebody who is signed in, the other shows the panel to
                // somebody who is not.
                SessionViewModel.State.Unknown ->
                    Box(Modifier.fillMaxSize().background(AdminTokens.canvas))

                SessionViewModel.State.SignedOut -> SignInHost(sessions::refresh)

                is SessionViewModel.State.SignedIn ->
                    SignedInArea(
                        session = state.session,
                        router = router,
                        onSignOut = sessions::signOut,
                        collapsed = collapsed,
                        onToggleCollapsed = {
                            collapsed = !collapsed
                            preferences.railCollapsed = collapsed
                        },
                        dark = dark,
                        onToggleTheme = {
                            dark = !dark
                            preferences.darkTheme = dark
                        },
                    )
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
            // open, and [SignedInArea] is what knows.
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
 * ViewModel a host resolves — off that shape. `:webApp` learned this the hard way.
 *
 * The permission check here is the second of three for the same fact. The rail
 * already hid what this role cannot open; this refuses it when the URL is typed
 * anyway; and RLS refuses the data underneath regardless.
 */
@Composable
private fun SignedInArea(
    session: AdminSession,
    router: Router,
    onSignOut: () -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit,
) {
    // The header's search box. Page-scoped rather than per-section, because the
    // design puts one box in the chrome and each section decides what to do with
    // whatever is in it.
    var search by remember { mutableStateOf("") }

    when (val route = router.current) {
        // Already signed in, so the sign-in URL has nothing to show. `replace`,
        // not `go`: it should not become a back-button stop.
        AdminRoute.SignIn -> {
            val landing = landingFor(session)
            LaunchedEffect(landing) { landing?.let(router::replace) }
            AdminShell(session, route, search, { search = it }, router::go, onSignOut, collapsed, onToggleCollapsed, dark, onToggleTheme) {
                // Nothing to land on is an account that is staff and holds no
                // roles. Saying so beats a blank page while a redirect that
                // cannot happen never fires.
                if (landing == null) NoRolesScreen() else Box(Modifier.fillMaxSize())
            }
        }

        else -> AdminShell(session, route, search, { search = it }, router::go, onSignOut, collapsed, onToggleCollapsed, dark, onToggleTheme) {
            when {
                route is AdminRoute.NotFound -> NotFoundScreen()
                !session.mayOpen(route) -> NoAccessScreen()
                // A section the design calls for and nothing backs yet. Stated
                // rather than filled with sample numbers.
                !route.built -> NotBuiltScreen(route)
                route is AdminRoute.Dashboard -> DashboardHost()
                route is AdminRoute.Cities -> CitiesHost(search)
                route is AdminRoute.Vehicles -> VehiclesHost(search)
                route is AdminRoute.Users -> UsersHost(search)
                route is AdminRoute.Roles -> RolesHost(session)
                route is AdminRoute.Content -> ContentHost(search, router::go)
                route is AdminRoute.PostDetail -> PostDetailHost(route.id) { router.go(AdminRoute.Content) }
                route is AdminRoute.Catalogue -> CatalogueHost(search)
                route is AdminRoute.Tickets -> TicketsHost(search) { router.go(AdminRoute.TicketDetail(it)) }
                route is AdminRoute.TicketDetail -> TicketDetailHost(route.id) { router.go(AdminRoute.Tickets) }
                route is AdminRoute.Billing -> BillingHost(search)
                route is AdminRoute.Flags -> FlagsHost(search)
                route is AdminRoute.Social -> SocialHost()
                route is AdminRoute.Audit -> AuditHost(search)
                else -> NotBuiltScreen(route)
            }
        }
    }
}

/**
 * Each section, resolved inside the shell's content slot.
 *
 * That is where their lifetime belongs: leaving a section disposes its ViewModel,
 * and coming back re-reads rather than showing a list that may be minutes stale.
 *
 * The header's search term is pushed down rather than each screen keeping its own.
 * One box in the chrome is what the design has, and two search fields on a page is
 * one too many.
 */
@Composable
private fun DashboardHost() {
    val viewModel: DashboardViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    DashboardScreen(state, viewModel::onEvent)
}

@Composable
private fun CitiesHost(search: String) {
    val viewModel: CitiesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(CitiesEvent.SearchChanged(search)) }
    CitiesScreen(state, viewModel::onEvent)
}

@Composable
private fun VehiclesHost(search: String) {
    val viewModel: VehiclesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(VehiclesEvent.SearchChanged(search)) }
    VehiclesScreen(state, viewModel::onEvent)
}

@Composable
private fun UsersHost(search: String) {
    val viewModel: UsersViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(UsersEvent.QueryChanged(search)) }
    UsersScreen(state, viewModel::onEvent)
}

@Composable
private fun RolesHost(session: AdminSession) {
    val viewModel: RolesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(session.id) { viewModel.onEvent(RolesEvent.SelfIdentified(session.id)) }
    RolesScreen(state, viewModel::onEvent)
}

@Composable
private fun AuditHost(search: String) {
    val viewModel: AuditViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(AuditEvent.FilterChanged(search)) }
    AuditScreen(state, viewModel::onEvent)
}

/** The blog's posts. Publish, unpublish, delete a draft. */
@Composable
private fun ContentHost(search: String, go: (AdminRoute) -> Unit) {
    val viewModel: ContentViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(ContentEvent.SearchChanged(search)) }
    ContentScreen(state, viewModel::onEvent) { go(AdminRoute.PostDetail(it)) }
}

/**
 * One post, read and edited in the panel.
 *
 * The id is a parameter rather than state, so the view model is rebuilt when the
 * URL changes — two posts opened in turn are two different loads, and a shared
 * instance would show the first one's body under the second one's title.
 */
@Composable
private fun PostDetailHost(id: String, onBack: () -> Unit) {
    val viewModel: PostDetailViewModel = koinViewModel(key = id) { parametersOf(id) }
    val state by viewModel.state.collectAsState()
    PostDetailScreen(state, viewModel::onEvent, onBack)
}

/** The app_config table: one row per remotely-set key. */
@Composable
private fun FlagsHost(search: String) {
    val viewModel: FlagsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(FlagsEvent.SearchChanged(search)) }
    FlagsScreen(state, viewModel::onEvent)
}

/** The social pipeline: posting mode, schedule, accounts, approvals, fact bank. */
@Composable
private fun SocialHost() {
    val viewModel: SocialViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    // No search wiring: the section is tabs over six short lists, and a search box that
    // filtered one of them would be a control whose meaning changed with the tab.
    SocialScreen(state, viewModel::onEvent)
}

/** Service items: intervals and cost benchmarks. */
@Composable
private fun CatalogueHost(search: String) {
    val viewModel: CatalogueViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(CatalogueEvent.SearchChanged(search)) }
    CatalogueScreen(state, viewModel::onEvent)
}

/** The support queue. */
@Composable
private fun TicketsHost(search: String, onOpen: (Long) -> Unit) {
    val viewModel: TicketsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(TicketsEvent.SearchChanged(search)) }
    TicketsScreen(state, viewModel::onEvent, onOpen)
}

/**
 * One ticket.
 *
 * Shares [TicketsViewModel] with the queue by resolving the same Koin view model,
 * so opening a ticket does not re-read the list and going back does not either.
 */
@Composable
private fun TicketDetailHost(id: Long, onBack: () -> Unit) {
    val viewModel: TicketsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    TicketDetailScreen(id, state, viewModel::onEvent, onBack)
}

/** Subscriptions, read-only. */
@Composable
private fun BillingHost(search: String) {
    val viewModel: BillingViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(search) { viewModel.onEvent(BillingEvent.SearchChanged(search)) }
    BillingScreen(state, viewModel::onEvent)
}
