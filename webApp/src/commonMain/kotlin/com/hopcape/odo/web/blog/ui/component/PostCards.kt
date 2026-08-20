package com.hopcape.odo.web.blog.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_reading_minutes
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.stringResource

/**
 * The card the whole blog is built out of: an index, a category, an author page
 * and a 404 are all the same list with different copy above it.
 *
 * [highlight] is the search term. It is drawn here rather than by the search
 * screen because the card owns the title's typography, and a caller that had to
 * build its own `AnnotatedString` would have to know that typography too.
 */
@Composable
fun PostCard(
    post: PostSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    showDate: Boolean = true,
) {
    val colors = BlogThemeTokens.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) colors.surfaceRaised else colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Eyebrow(post.category.name)
        Text(
            text = post.title.withHighlight(highlight),
            color = colors.text,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = post.dek,
            color = colors.dim,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        PostMeta(post, showDate)
    }
}

/**
 * The one story at the top of the index.
 *
 * A bigger card rather than a different component: the difference between a lead
 * and a card is the type scale and the room it gets, and modelling it as its own
 * thing would mean maintaining the same hover, the same border and the same meta
 * line twice.
 */
@Composable
fun LeadStory(
    post: PostSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (hovered) colors.surfaceRaised else colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(if (compact) 22.dp else 34.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Eyebrow(post.category.name)
        Text(
            text = post.title,
            color = colors.text,
            style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
        )
        Text(
            text = post.dek,
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        PostMeta(post, showDate = true)
    }
}

/** The date and reading time under a title. */
@Composable
fun PostMeta(post: PostSummary, showDate: Boolean = true, modifier: Modifier = Modifier) {
    val colors = BlogThemeTokens.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showDate) {
            Text(post.publishedOn.formatted(), color = colors.muted, style = MaterialTheme.typography.bodySmall)
            Text("·", color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = stringResource(Res.string.bl_reading_minutes, post.readingMinutes),
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * "18 Aug 2026".
 *
 * Written out rather than taken from a formatter because kotlinx-datetime's
 * localised formats would follow the browser's locale, and this site is one
 * language in one country — a date that renders as 8/18/26 for some readers and
 * 18/08/2026 for others is a difference nobody asked for.
 */
fun LocalDate.formatted(): String {
    val month = when (month) {
        Month.JANUARY -> "Jan"
        Month.FEBRUARY -> "Feb"
        Month.MARCH -> "Mar"
        Month.APRIL -> "Apr"
        Month.MAY -> "May"
        Month.JUNE -> "Jun"
        Month.JULY -> "Jul"
        Month.AUGUST -> "Aug"
        Month.SEPTEMBER -> "Sep"
        Month.OCTOBER -> "Oct"
        Month.NOVEMBER -> "Nov"
        Month.DECEMBER -> "Dec"
        else -> ""
    }
    return "${day.toString().padStart(2, '0')} $month $year"
}

/**
 * Bolds the searched-for words inside a title.
 *
 * Matches whole words case-insensitively, so searching "challan" marks it inside
 * "Challan kaise check karein" without also marking the "chal" in something else.
 */
private fun String.withHighlight(term: String?): AnnotatedString {
    if (term.isNullOrBlank()) return AnnotatedString(this)
    val words = term.split(' ').filter { it.isNotBlank() }
    return buildAnnotatedString {
        append(this@withHighlight)
        words.forEach { word ->
            var from = indexOf(word, ignoreCase = true)
            while (from >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), from, from + word.length)
                from = indexOf(word, startIndex = from + word.length, ignoreCase = true)
            }
        }
    }
}
