package com.hopcape.odo.web.admin.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.AdminSession
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_sign_out
import com.hopcape.odo.web.admin.resources.ad_signin_title
import com.hopcape.odo.web.admin.routing.AdminRoute
import com.hopcape.odo.web.admin.routing.sectionsFor
import com.hopcape.odo.web.admin.ui.label
import org.jetbrains.compose.resources.stringResource

/**
 * The frame every signed-in page is drawn inside: a rail on the left, content on
 * the right.
 *
 * **The rail only lists what this session may open.** That is a courtesy, not the
 * control — the same permission is checked again when a route is opened by URL,
 * and RLS refuses the data underneath either way. Three checks for one fact, and
 * only the last one is load-bearing.
 *
 * A rail rather than a top bar because the section list grows: this epic adds
 * seven and #370 nests a CMS under one of them. Horizontal space runs out; a
 * column scrolls.
 */
@Composable
fun AdminShell(
    session: AdminSession,
    current: AdminRoute,
    onNavigate: (AdminRoute) -> Unit,
    onSignOut: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Rail(session, current, onNavigate, onSignOut)
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun Rail(
    session: AdminSession,
    current: AdminRoute,
    onNavigate: (AdminRoute) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.ad_signin_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sectionsFor(session).forEach { route ->
                RailItem(
                    label = route.label(),
                    selected = route == current,
                    onClick = { onNavigate(route) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Account(session, onSignOut)
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                // The selected row is filled rather than merely coloured: with
                // seven near-identical rows, a weight difference alone is easy to
                // lose track of when the page behind it changes.
                if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Who is signed in, at the bottom of the rail.
 *
 * The address, not just the name. Two people can share a display name and the
 * whole point of the audit log is knowing which account did something — so the
 * panel shows the same thing the audit log will attribute a change to.
 */
@Composable
private fun Account(session: AdminSession, onSignOut: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = session.email,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.padding(top = 4.dp))
        TextButton(onClick = onSignOut, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(stringResource(Res.string.ad_sign_out), style = MaterialTheme.typography.labelLarge)
        }
    }
}
