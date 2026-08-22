package com.hopcape.odo.web.blog.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.hopcape.odo.web.blog.domain.model.Session
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_admin_nav_analytics
import com.hopcape.odo.web.blog.resources.bl_admin_nav_media
import com.hopcape.odo.web.blog.resources.bl_admin_nav_posts
import com.hopcape.odo.web.blog.resources.bl_admin_nav_settings
import com.hopcape.odo.web.blog.resources.bl_admin_sign_out
import com.hopcape.odo.web.blog.resources.bl_brand
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.routing.BlogRoute.Admin
import com.hopcape.odo.web.blog.ui.component.Hairline
import com.hopcape.odo.web.blog.ui.component.InitialAvatar
import com.hopcape.odo.web.blog.ui.component.TextLink
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

private val ADMIN_MAX = 1180.dp

/**
 * The CMS frame: brand, four tabs, who is signed in.
 *
 * White, unlike everything the reader sees. That is the design's decision and its
 * reason is worth keeping: this is where somebody sits for an hour writing, and
 * long-form typing on black is tiring in a way reading it is not.
 *
 * The editor does not use this shell. It replaces the tabs with its own toolbar,
 * because a page that is one long text field should not spend its top edge on
 * navigation away from itself.
 */
@Composable
fun AdminShell(
    current: Admin,
    session: Session,
    onNavigate: (BlogRoute) -> Unit,
    onSignOut: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .widthIn(max = ADMIN_MAX)
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                TextLink(
                    text = stringResource(Res.string.bl_brand),
                    onClick = { onNavigate(Admin.Posts) },
                    color = colors.text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.34.em,
                    ),
                )
                if (!compact) {
                    Tab(stringResource(Res.string.bl_admin_nav_posts), current is Admin.Posts) {
                        onNavigate(Admin.Posts)
                    }
                    Tab(stringResource(Res.string.bl_admin_nav_media), current is Admin.Media) {
                        onNavigate(Admin.Media)
                    }
                    Tab(stringResource(Res.string.bl_admin_nav_analytics), current is Admin.Analytics) {
                        onNavigate(Admin.Analytics)
                    }
                    Tab(stringResource(Res.string.bl_admin_nav_settings), current is Admin.Settings) {
                        onNavigate(Admin.Settings)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextLink(stringResource(Res.string.bl_admin_sign_out), onSignOut, color = colors.muted)
                InitialAvatar(session.initial, diameter = 30)
            }
        }
        Hairline()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ADMIN_MAX)
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 20.dp else 40.dp)
                    .padding(top = 34.dp, bottom = 64.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BlogThemeTokens.colors
    TextLink(
        text = label,
        onClick = onClick,
        color = if (selected) colors.text else colors.muted,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        ),
    )
}
