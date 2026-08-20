package com.hopcape.odo.web.blog.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens

/**
 * The card grid: two columns on a desktop, one on a phone.
 *
 * [FlowRow] rather than a lazy grid because these lists are tens of items, not
 * thousands, and they sit inside a page that scrolls as a whole. A lazy grid
 * inside a scrolling column has to be given a height, which is exactly the
 * constraint a page of cards should not have.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostGrid(
    posts: List<PostSummary>,
    onOpen: (PostSummary) -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    columns: Int = if (BlogThemeTokens.compact) 1 else 2,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
        maxItemsInEachRow = columns,
    ) {
        posts.forEach { post ->
            PostCard(
                post = post,
                onClick = { onOpen(post) },
                highlight = highlight,
                // weight rather than a fixed width, so the last row of an odd
                // list still lines its card up with the column above it; and
                // fillMaxRowHeight so a two-line title does not leave the card
                // beside it standing shorter.
                //
                // Only when there is something to match. A lone card in a row
                // whose other cell is a filler Spacer has no sibling to take its
                // height from, and stretches to whatever the row was offered.
                modifier = Modifier
                    .weight(1f)
                    .then(if (posts.size > 1) Modifier.fillMaxRowHeight() else Modifier),
            )
        }
        // An odd count would otherwise stretch the final card across the row.
        if (columns > 1 && posts.size % columns != 0) {
            repeat(columns - posts.size % columns) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val GAP = 18.dp
