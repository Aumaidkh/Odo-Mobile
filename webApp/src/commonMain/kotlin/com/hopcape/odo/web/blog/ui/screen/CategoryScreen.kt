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
import com.hopcape.odo.web.blog.presentation.category.CategoryEvent
import com.hopcape.odo.web.blog.presentation.category.CategoryUiState
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_category_count_many
import com.hopcape.odo.web.blog.resources.bl_category_count_one
import com.hopcape.odo.web.blog.resources.bl_category_eyebrow
import com.hopcape.odo.web.blog.resources.bl_category_more_coming
import com.hopcape.odo.web.blog.resources.bl_category_more_coming_dek
import com.hopcape.odo.web.blog.resources.bl_subscribe_action
import com.hopcape.odo.web.blog.resources.bl_subscribe_dek
import com.hopcape.odo.web.blog.resources.bl_subscribe_done
import com.hopcape.odo.web.blog.resources.bl_subscribe_heading
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.EmailCapture
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PostGrid
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * Every article in one category.
 *
 * A category with one or two posts gets the "more coming" block and the email
 * capture instead of an apology. A page that admits it is thin and offers to tell
 * you when it is not is worth more than a page that pretends.
 */
@Composable
fun CategoryScreen(
    state: CategoryUiState,
    onEvent: (CategoryEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    LoadableBox(state.page, onRetry = { onEvent(CategoryEvent.Retry) }) { page ->
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Eyebrow(stringResource(Res.string.bl_category_eyebrow))
                Text(
                    text = page.category.name,
                    color = colors.text,
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    // The count comes from the posts actually filed here, so a
                    // category can never advertise articles nobody wrote.
                    text = if (page.posts.size == 1) {
                        stringResource(Res.string.bl_category_count_one, page.category.blurb)
                    } else {
                        stringResource(Res.string.bl_category_count_many, page.category.blurb, page.posts.size)
                    },
                    color = colors.dim,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 620.dp),
                )
            }

            PostGrid(
                posts = page.posts,
                onOpen = { onNavigate(BlogRoute.Public.Article(it.slug)) },
            )

            if (state.isThin) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(Res.string.bl_category_more_coming),
                            color = colors.text,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = stringResource(Res.string.bl_category_more_coming_dek),
                            color = colors.dim,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    EmailCapture(
                        heading = stringResource(Res.string.bl_subscribe_heading),
                        dek = stringResource(Res.string.bl_subscribe_dek),
                        action = stringResource(Res.string.bl_subscribe_action),
                        doneMessage = stringResource(Res.string.bl_subscribe_done),
                        email = state.email,
                        submission = state.subscription,
                        onEmailChange = { onEvent(CategoryEvent.EmailChanged(it)) },
                        onSubmit = { onEvent(CategoryEvent.Subscribe) },
                    )
                }
            }
        }
    }
}
