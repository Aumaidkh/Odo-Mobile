package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_no_access_body
import com.hopcape.odo.web.admin.resources.ad_no_access_title
import com.hopcape.odo.web.admin.resources.ad_no_roles_body
import com.hopcape.odo.web.admin.resources.ad_no_roles_title
import com.hopcape.odo.web.admin.resources.ad_not_found_body
import com.hopcape.odo.web.admin.resources.ad_not_found_title
import com.hopcape.odo.web.admin.resources.ad_placeholder_body
import com.hopcape.odo.web.admin.routing.AdminRoute
import com.hopcape.odo.web.admin.ui.label
import org.jetbrains.compose.resources.stringResource

/**
 * A section that has a route, a permission and a nav item, and no screen yet.
 *
 * Every one of #366 to #370 replaces one of these. They exist rather than being
 * added later because the nav's shape is what the permission model is tested
 * against — a rail that lists only the two sections built so far would not show
 * whether a role hides the right things.
 */
@Composable
fun PlaceholderScreen(route: AdminRoute) {
    Message(title = route.label(), body = stringResource(Res.string.ad_placeholder_body))
}

/**
 * A route this role does not cover, reached by typing its URL.
 *
 * The copy says the server would refuse it too, on purpose. Somebody who works
 * out that the nav is only hiding things should also learn, in the same sentence,
 * that going around the nav gains them nothing.
 */
@Composable
fun NoAccessScreen() {
    Message(
        title = stringResource(Res.string.ad_no_access_title),
        body = stringResource(Res.string.ad_no_access_body),
    )
}

/** Staff, but holding no roles — so there is no section to send them to. */
@Composable
fun NoRolesScreen() {
    Message(
        title = stringResource(Res.string.ad_no_roles_title),
        body = stringResource(Res.string.ad_no_roles_body),
    )
}

@Composable
fun NotFoundScreen() {
    Message(
        title = stringResource(Res.string.ad_not_found_title),
        body = stringResource(Res.string.ad_not_found_body),
    )
}

/**
 * One shape for every "there is nothing to do here" page.
 *
 * Top-left rather than centred: it sits beside a rail, and a block of text
 * centred in the remaining space lines up with nothing.
 */
@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}
