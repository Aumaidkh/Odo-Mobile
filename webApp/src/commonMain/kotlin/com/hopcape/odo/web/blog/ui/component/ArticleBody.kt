package com.hopcape.odo.web.blog.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.TextRun
import com.hopcape.odo.web.blog.platform.PLAY_LISTING
import com.hopcape.odo.web.blog.platform.openExternal
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_action_eyebrow
import com.hopcape.odo.web.blog.resources.bl_action_screenshot_slot
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * Draws an article body.
 *
 * The `when` is exhaustive over [ArticleBlock], which is the contract between the
 * editor and the page: a block that can be written is a block that can be drawn,
 * and adding one to the model without drawing it stops compiling here.
 */
@Composable
fun ArticleBody(
    blocks: List<ArticleBlock>,
    modifier: Modifier = Modifier,
) {
    val colors = BlogThemeTokens.colors
    Column(modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is ArticleBlock.Section -> Text(
                    text = block.text,
                    color = colors.text,
                    style = MaterialTheme.typography.headlineLarge,
                    // More space above than below: a heading belongs to what
                    // follows it, and equal margins make it float between two
                    // sections instead of opening one.
                    modifier = Modifier.padding(top = 44.dp, bottom = 14.dp),
                )

                is ArticleBlock.Paragraph -> Text(
                    text = block.runs.annotated(),
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 18.dp),
                )

                is ArticleBlock.Callout -> Callout(block)

                is ArticleBlock.AppShowcase -> AppShowcase(block)
            }
        }
    }
}

/**
 * The boxed aside.
 *
 * A filled card with an amber rule down its left edge — not a border on all four
 * sides. The rule is what makes it read as an aside rather than a second article:
 * it marks the block without drawing a box around it, so the eye keeps its place
 * in the column.
 *
 * Amber, not red. The callout warns about a deadline; it does not report a
 * failure, and red on a reading page reads as an error.
 */
@Composable
private fun Callout(block: ArticleBlock.Callout) {
    val colors = BlogThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(CALLOUT_RULE)
                .fillMaxHeight()
                .background(colors.warning),
        )
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow(block.label, color = colors.warning)
            Text(
                text = block.runs.annotated(),
                color = colors.dim,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * The app, inside the answer — the one thing on the page that asks for something.
 *
 * It sits where the design puts it: after the prose has already answered the
 * question, so the page is useful to somebody who never installs anything. A blog
 * that gates its answer behind a download stops being found, which defeats the
 * only reason it exists.
 *
 * The eyebrow says "shown inside the article" because that is what an author
 * needs to know while placing it, and a reader will never wonder.
 */
@Composable
private fun AppShowcase(block: ArticleBlock.AppShowcase) {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceRaised)
            .padding(horizontal = 28.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Eyebrow(stringResource(Res.string.bl_action_eyebrow))
        Text(block.heading, color = colors.text, style = MaterialTheme.typography.headlineMedium)
        Text(block.body, color = colors.dim, style = MaterialTheme.typography.titleLarge)
        PillButton(
            text = block.callToAction,
            onClick = { openExternal(PLAY_LISTING) },
            modifier = Modifier.padding(top = 6.dp),
        )
        // The slot, named. An author who has not dropped a capture in yet should be
        // told so here rather than finding out from the published page.
        if (block.screenshot.isNullOrBlank()) {
            Text(
                text = stringResource(Res.string.bl_action_screenshot_slot),
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            )
        }
    }
}

/** Four pixels of amber. Thin enough to mark the block, not to box it. */
private val CALLOUT_RULE = 4.dp

/** Runs into one styled string. */
internal fun List<TextRun>.annotated(): AnnotatedString = buildAnnotatedString {
    this@annotated.forEach { run ->
        val start = length
        append(run.text)
        if (run.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
        if (run.italic) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
    }
}
