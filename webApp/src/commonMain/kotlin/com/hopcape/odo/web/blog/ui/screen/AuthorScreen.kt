package com.hopcape.odo.web.blog.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.AuthorPage
import com.hopcape.odo.web.blog.presentation.author.AuthorEvent
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_author_articles
import com.hopcape.odo.web.blog.resources.bl_author_posts_heading
import com.hopcape.odo.web.blog.resources.bl_author_since
import com.hopcape.odo.web.blog.resources.bl_author_writes_about
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.InitialAvatar
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PostGrid
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * One author.
 *
 * It exists because a byline that links nowhere is a byline nobody trusts. On a
 * site whose whole claim is "these numbers came from real bills", the person
 * making that claim has to be a page.
 */
@Composable
fun AuthorScreen(
    state: Loadable<AuthorPage>,
    onEvent: (AuthorEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    LoadableBox(state, onRetry = { onEvent(AuthorEvent.Retry) }) { page ->
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    InitialAvatar(page.author.initial, diameter = 52)
                    Text(
                        text = page.author.name,
                        color = colors.text,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = page.author.bio,
                    color = colors.dim,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 620.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Stat(stringResource(Res.string.bl_author_articles), page.posts.size.toString())
                    Stat(stringResource(Res.string.bl_author_writes_about), page.author.topics)
                    Stat(stringResource(Res.string.bl_author_since), page.author.since)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // The author's own first name, because "RAHUL KE ARTICLES" is how
                // the design says it and a full name there reads like a byline.
                Eyebrow(
                    stringResource(
                        Res.string.bl_author_posts_heading,
                        page.author.name.substringBefore(' '),
                    ),
                )
                PostGrid(
                    posts = page.posts,
                    onOpen = { onNavigate(BlogRoute.Public.Article(it.slug)) },
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val colors = BlogThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Eyebrow(label)
        Text(value, color = colors.text, style = MaterialTheme.typography.titleMedium)
    }
}
