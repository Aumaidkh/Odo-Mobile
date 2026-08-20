package com.hopcape.odo.web.blog.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.TextRun
import com.hopcape.odo.web.blog.platform.PLAY_LISTING
import com.hopcape.odo.web.blog.platform.openExternal
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens

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

/** The boxed aside. Bordered rather than filled, so it reads as quieter, not louder. */
@Composable
private fun Callout(block: ArticleBlock.Callout) {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // Amber, not red: the design's callout warns about a deadline, it does
        // not report a failure, and red on a reading page reads as an error.
        Eyebrow(block.label, color = colors.warning)
        Text(
            text = block.runs.annotated(),
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * The app, inside the answer.
 *
 * It sits where the design puts it — after the prose has already answered the
 * question — so the page is useful to somebody who never installs anything. A
 * blog that gates its answer behind a download stops being found.
 */
@Composable
private fun AppShowcase(block: ArticleBlock.AppShowcase) {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderStrong, RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(block.heading, color = colors.text, style = MaterialTheme.typography.headlineSmall)
        Text(block.body, color = colors.dim, style = MaterialTheme.typography.bodyLarge)
        PillButton(block.callToAction, onClick = { openExternal(PLAY_LISTING) })
    }
}

/** Runs into one styled string. Bold is the only emphasis the design uses. */
private fun List<TextRun>.annotated(): AnnotatedString = buildAnnotatedString {
    this@annotated.forEach { run ->
        if (run.bold) {
            withStyleBold { append(run.text) }
        } else {
            append(run.text)
        }
    }
}

private inline fun AnnotatedString.Builder.withStyleBold(block: AnnotatedString.Builder.() -> Unit) {
    val start = length
    block()
    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
}
