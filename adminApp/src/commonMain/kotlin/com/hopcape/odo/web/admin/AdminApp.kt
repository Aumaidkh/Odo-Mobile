package com.hopcape.odo.web.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.presentation.SessionViewModel
import com.hopcape.odo.web.admin.presentation.signin.SignInEffect
import com.hopcape.odo.web.admin.presentation.signin.SignInViewModel
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_session_none
import com.hopcape.odo.web.admin.resources.ad_session_permissions
import com.hopcape.odo.web.admin.resources.ad_session_roles
import com.hopcape.odo.web.admin.resources.ad_session_route
import com.hopcape.odo.web.admin.resources.ad_sign_out
import com.hopcape.odo.web.admin.routing.AdminRoute
import com.hopcape.odo.web.admin.routing.Router
import com.hopcape.odo.web.admin.ui.screen.SignInScreen
import com.hopcape.odo.web.admin.ui.theme.AdminTheme
import com.hopcape.odo.web.core.presentation.CollectEffects
import com.hopcape.odo.web.core.presentation.RouteScope
import org.jetbrains.compose.resources.stringResource
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
 *
 * **This is S4's shell, not the finished one.** The role-gated nav, the per-section
 * route guard and the placeholder screens are S5; what is here proves the sign-in
 * chain end to end in a browser and nothing more.
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

                SessionViewModel.State.SignedOut -> SignInHost(router, sessions::refresh)

                is SessionViewModel.State.SignedIn ->
                    SignedIn(state, router, sessions::signOut)
            }
        }
    }
}

@Composable
private fun SignInHost(router: Router, onSignedIn: () -> Unit) {
    val viewModel: SignInViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SignInEffect.SignedIn -> {
                onSignedIn()
                // The sign-in page is not somewhere to stay once it has worked.
                // Where to go instead depends on what this admin may do, which S5's
                // nav decides; for now anything but the sign-in URL will do.
                if (router.current is AdminRoute.SignIn) router.replace(AdminRoute.Vehicles)
            }
        }
    }

    SignInScreen(state, viewModel::onEvent)
}

/**
 * Everything behind the gate — a placeholder until S5 builds the real shell.
 *
 * It draws the identity rather than a welcome message on purpose: this is the
 * screen that shows, in a browser, that the Firebase exchange, the edge function,
 * the account binding and `my_admin_identity()` all agree about who signed in.
 */
@Composable
private fun SignedIn(
    state: SessionViewModel.State.SignedIn,
    router: Router,
    onSignOut: () -> Unit,
) {
    val session = state.session
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(session.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            session.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val none = stringResource(Res.string.ad_session_none)
        Text(
            stringResource(Res.string.ad_session_roles, session.roles.joinToString().ifBlank { none }),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                Res.string.ad_session_permissions,
                session.permissions.joinToString { it.id }.ifBlank { none },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.ad_session_route, router.current.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(Res.string.ad_sign_out))
        }
    }
}
