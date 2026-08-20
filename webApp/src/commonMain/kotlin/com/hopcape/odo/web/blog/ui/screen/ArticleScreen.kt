package com.hopcape.odo.web.blog.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.Article
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.presentation.article.ArticleEvent
import com.hopcape.odo.web.blog.presentation.article.ArticleUiState
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_article_contents
import com.hopcape.odo.web.blog.resources.bl_article_contents_count
import com.hopcape.odo.web.blog.resources.bl_article_read_next
import com.hopcape.odo.web.blog.resources.bl_reading_minutes
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.ArticleBody
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.InitialAvatar
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PostGrid
import com.hopcape.odo.web.blog.ui.component.TextLink
import com.hopcape.odo.web.blog.ui.component.formatted
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/** The design's reading measure. Wider than this and long Hinglish lines get lost. */
private val MEASURE = 680.dp
private val RAIL = 210.dp

/**
 * One article.
 *
 * On a wide screen the contents sit in a rail to the left of the measure; on a
 * phone they collapse into a strip that opens, because a rail beside a 390px
 * column would leave nothing for the column.
 *
 * The rail scrolls with the page rather than sticking to the top of the viewport.
 * The design has it stick, and doing that properly needs the article to own its
 * own scroll instead of the shell — worth doing, not worth blocking the page on.
 */
@Composable
fun ArticleScreen(
    state: ArticleUiState,
    onEvent: (ArticleEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    LoadableBox(state.article, onRetry = { onEvent(ArticleEvent.Retry) }) { article ->
        if (BlogThemeTokens.compact) {
            CompactArticle(article, state, onEvent, onNavigate)
        } else {
            WideArticle(article, state, onEvent, onNavigate)
        }
    }
}

@Composable
private fun WideArticle(
    article: Article,
    state: ArticleUiState,
    onEvent: (ArticleEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        ContentsRail(article.contents, state.activeSection, onEvent, Modifier.width(RAIL))
        Column(Modifier.widthIn(max = MEASURE)) {
            ArticleHeader(article, onNavigate)
            ArticleBody(article.body)
            ReadNext(article, onNavigate)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CompactArticle(
    article: Article,
    state: ArticleUiState,
    onEvent: (ArticleEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    Column {
        ArticleHeader(article, onNavigate)
        ContentsStrip(article.contents, state.contentsExpanded, onEvent)
        ArticleBody(article.body)
        ReadNext(article, onNavigate)
    }
}

@Composable
private fun ArticleHeader(article: Article, onNavigate: (BlogRoute) -> Unit) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 10.dp)) {
        TextLink(
            text = article.summary.category.name.uppercase(),
            onClick = { onNavigate(BlogRoute.Public.Category(article.summary.category.slug)) },
            color = colors.muted,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = article.summary.title,
            color = colors.text,
            style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
        )
        Text(
            text = article.summary.dek,
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            InitialAvatar(article.author.initial)
            Column {
                TextLink(
                    text = article.author.name,
                    onClick = { onNavigate(BlogRoute.Public.Author(article.author.slug)) },
                    color = colors.text,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = article.summary.publishedOn.formatted() + " · " +
                        stringResource(Res.string.bl_reading_minutes, article.summary.readingMinutes),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ContentsRail(
    sections: List<ArticleBlock.Section>,
    active: String?,
    onEvent: (ArticleEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlogThemeTokens.colors
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(stringResource(Res.string.bl_article_contents))
        sections.forEach { section ->
            TextLink(
                text = section.text,
                onClick = { onEvent(ArticleEvent.SectionSelected(section.id)) },
                color = if (section.id == active) colors.text else colors.dim,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
    }
}

/** The phone's contents: one line that opens into the list. */
@Composable
private fun ContentsStrip(
    sections: List<ArticleBlock.Section>,
    expanded: Boolean,
    onEvent: (ArticleEvent) -> Unit,
) {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onEvent(ArticleEvent.ContentsToggled) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.bl_article_contents_count, sections.size),
            color = colors.dim,
            style = MaterialTheme.typography.labelLarge,
        )
        if (expanded) {
            sections.forEach { section ->
                TextLink(
                    text = section.text,
                    onClick = { onEvent(ArticleEvent.SectionSelected(section.id)) },
                    color = colors.dim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Two posts from other categories.
 *
 * Which two is the repository's decision, not this screen's — somebody who
 * finished an article about challans is done with challans, and the next click
 * should widen rather than repeat.
 */
@Composable
private fun ReadNext(article: Article, onNavigate: (BlogRoute) -> Unit) {
    if (article.readNext.isEmpty()) return
    Column(
        modifier = Modifier.padding(top = 56.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Eyebrow(stringResource(Res.string.bl_article_read_next))
        PostGrid(
            posts = article.readNext,
            onOpen = { onNavigate(BlogRoute.Public.Article(it.slug)) },
        )
    }
}
