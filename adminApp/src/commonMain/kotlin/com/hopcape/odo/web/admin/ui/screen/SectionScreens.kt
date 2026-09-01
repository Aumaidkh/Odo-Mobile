package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.hopcape.odo.web.admin.resources.ad_notbuilt_title
import com.hopcape.odo.web.admin.resources.ad_placeholder_body
import com.hopcape.odo.web.admin.routing.AdminRoute
import com.hopcape.odo.web.admin.ui.label
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import org.jetbrains.compose.resources.stringResource

/**
 * A section the design calls for and nothing backs yet.
 *
 * Says so, rather than showing sample numbers. In a staff tool a real figure and
 * a placeholder look identical, which is how somebody ends up quoting one.
 */
@Composable
fun NotBuiltScreen(route: AdminRoute) {
    Message(
        title = stringResource(Res.string.ad_notbuilt_title, route.label()),
        body = stringResource(Res.string.ad_placeholder_body),
    )
}

/**
 * A route this role does not cover, reached by typing its URL.
 *
 * The copy says the server would refuse it too, on purpose: somebody who works out
 * that the nav is only hiding things should learn in the same sentence that going
 * around it gains them nothing.
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
 * Top-left rather than centred: it sits beside a rail, and a block of text centred
 * in the remaining space lines up with nothing.
 */
@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = AdminType.title, color = AdminTokens.text)
        Text(
            text = body,
            style = AdminType.body,
            color = AdminTokens.textFaint,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}
