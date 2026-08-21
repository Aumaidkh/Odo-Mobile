package com.hopcape.odo.web.blog.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.presentation.notfound.NotFoundEvent
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_not_found_code
import com.hopcape.odo.web.blog.resources.bl_not_found_dek
import com.hopcape.odo.web.blog.resources.bl_not_found_heading
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PostGrid
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * The dead end that is not a dead end.
 *
 * A reader here followed a link that no longer works — from a search result, a
 * WhatsApp forward, an old bookmark. They still wanted something. So the page
 * says what happened in one line and spends the rest of itself on the three
 * posts most likely to be what they were after.
 */
@Composable
fun NotFoundScreen(
    state: Loadable<List<PostSummary>>,
    onEvent: (NotFoundEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Eyebrow(stringResource(Res.string.bl_not_found_code))
            Text(
                text = stringResource(Res.string.bl_not_found_heading),
                color = colors.text,
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = stringResource(Res.string.bl_not_found_dek),
                color = colors.dim,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(max = 620.dp),
            )
        }

        LoadableBox(state, onRetry = { onEvent(NotFoundEvent.Retry) }) { posts ->
            PostGrid(
                posts = posts,
                onOpen = { onNavigate(BlogRoute.Public.Article(it.slug)) },
                columns = if (BlogThemeTokens.compact) 1 else 3,
            )
        }
    }
}
