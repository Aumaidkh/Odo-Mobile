package com.hopcape.odo.web.blog.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.presentation.state.resolve
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_retry
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * Draws the wait and the failure so a screen only has to draw the content.
 *
 * Every page in this app reads something before it can render, and without one
 * of these each would grow its own spinner and its own error copy. Two of them
 * would then disagree.
 *
 * There is no spinner, for the reason there never was: one that shows for 200ms
 * reads as a flicker. The wait is a skeleton instead — it holds the shape of what is
 * coming, so nothing jumps when the content lands, and it says how much is coming,
 * which the line of text it replaces could not.
 */
@Composable
fun <T> LoadableBox(
    state: Loadable<T>,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is Loadable.Loading -> LoadingSkeleton(modifier)

        is Loadable.Failed -> Column(
            modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = state.message.resolve(),
                color = BlogThemeTokens.colors.dim,
                style = MaterialTheme.typography.bodyLarge,
            )
            // Only offered when trying again could change the answer. A missing
            // post stays missing, and a button that never works teaches readers
            // that buttons do not work.
            if (state.retryable && onRetry != null) {
                PillButton(stringResource(Res.string.bl_retry), onRetry, filled = false)
            }
        }

        is Loadable.Ready -> content(state.value)
    }
}
