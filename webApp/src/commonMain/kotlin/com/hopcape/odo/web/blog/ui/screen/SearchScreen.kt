package com.hopcape.odo.web.blog.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.presentation.search.SearchEvent
import com.hopcape.odo.web.blog.presentation.search.SearchUiState
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_search_empty_dek
import com.hopcape.odo.web.blog.resources.bl_search_empty_heading
import com.hopcape.odo.web.blog.resources.bl_search_prompt
import com.hopcape.odo.web.blog.resources.bl_search_quoted
import com.hopcape.odo.web.blog.resources.bl_search_results_count
import com.hopcape.odo.web.blog.resources.bl_topic_request_action
import com.hopcape.odo.web.blog.resources.bl_topic_request_dek
import com.hopcape.odo.web.blog.resources.bl_topic_request_done
import com.hopcape.odo.web.blog.resources.bl_topic_request_heading
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.EmailCapture
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PostGrid
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * Search results, and the two states that are not results.
 *
 * Nothing found is not an empty page. The design's dead end offers what people
 * who searched the same thing usually read, and then offers to write the missing
 * article — which turns the one moment the site failed into the only place it
 * learns what to publish next.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    LoadableBox(state.results, onRetry = { onEvent(SearchEvent.Retry) }) { results ->
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            when {
                state.isPrompt -> Text(
                    text = stringResource(Res.string.bl_search_prompt),
                    color = colors.dim,
                    style = MaterialTheme.typography.displayMedium,
                )

                state.isDeadEnd -> Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Eyebrow(stringResource(Res.string.bl_search_quoted, results.query))
                    Text(
                        text = stringResource(Res.string.bl_search_empty_heading),
                        color = colors.text,
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        text = stringResource(Res.string.bl_search_empty_dek, results.query),
                        color = colors.dim,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.widthIn(max = 620.dp),
                    )
                }

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow(stringResource(Res.string.bl_search_results_count, results.hits.size))
                    Text(
                        text = stringResource(Res.string.bl_search_quoted, results.query),
                        color = colors.text,
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
            }

            // Hits when there are any, suggestions when there are not. Never both
            // — suggestions next to results would read as more results.
            PostGrid(
                posts = results.hits.ifEmpty { results.suggestions },
                onOpen = { onNavigate(BlogRoute.Public.Article(it.slug)) },
                highlight = results.query.takeIf { results.hits.isNotEmpty() },
            )

            if (state.isDeadEnd) {
                EmailCapture(
                    heading = stringResource(Res.string.bl_topic_request_heading),
                    dek = stringResource(Res.string.bl_topic_request_dek),
                    action = stringResource(Res.string.bl_topic_request_action),
                    doneMessage = stringResource(Res.string.bl_topic_request_done),
                    email = state.email,
                    submission = state.request,
                    onEmailChange = { onEvent(SearchEvent.EmailChanged(it)) },
                    onSubmit = { onEvent(SearchEvent.RequestTopic) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
