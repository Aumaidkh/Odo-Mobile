package com.hopcape.odo.feature.support.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The shape both confirmations share: a tick, a headline, a sentence, some facts, and the way
 * out.
 *
 * One layout rather than two, because the two screens differ only in what they list. There is
 * no top bar on either — the errand is over, and a back arrow on a confirmation invites the
 * owner back into a form they have already sent.
 */
@Composable
private fun SentScaffold(
    headline: String,
    intro: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        // Composed rather than an asset: a tick in a ring is a decoration these two screens
        // share, not a control the design system owes anybody.
        Box(
            modifier = Modifier
                .padding(top = OdoTheme.spacing.xl)
                .size(TICK_SIZE)
                .border(BorderStroke(TICK_RING, OdoTheme.colors.text), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(imageVector = IcCheck, contentDescription = null)
        }
        OdoText(text = headline, style = OdoTheme.typography.display)
        OdoText(
            text = intro,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
        content()
    }
}

/** A label-and-value row, as both confirmations list their facts. */
@Composable
internal fun SentRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OdoTheme.spacing.cardPadding,
                vertical = OdoTheme.spacing.md,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(text = label, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
        OdoText(text = value, style = OdoTheme.typography.body)
    }
}

/** A card of [SentRow]s, divided, with no padding of its own. */
@Composable
internal fun SentFactsCard(rows: List<Pair<String, String>>) {
    OdoCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) OdoDivider()
            SentRow(label = label, value = value)
        }
    }
}

private val TICK_SIZE = 44.dp
private val TICK_RING = 2.dp

/* ------------------------------ Diagnostics ------------------------------ */

@Composable
internal fun DiagnosticsSentScreen(
    reference: String,
    facts: List<Pair<String, String>>,
    referenceLabel: String,
    headline: String,
    intro: String,
    copyLabel: String,
    deleteLabel: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Neither confirmation has a top bar, and a bare column gets no insets from anywhere —
    // the tick lands under the clock and the button under the navigation bar.
    Column(modifier = modifier.fillMaxSize().systemBarsPadding()) {
        SentScaffold(
            headline = headline,
            intro = intro,
            modifier = Modifier.weight(1f),
        ) {
            OdoCard {
                OdoText(
                    text = referenceLabel,
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.textMuted,
                )
                // The whole point of the screen. Large enough to read off to somebody on a
                // phone call, which is how a reference actually gets used.
                OdoText(text = reference, style = OdoTheme.typography.heading)
            }
            SentFactsCard(facts)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OdoTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OdoButton(text = copyLabel, onClick = onCopy, modifier = Modifier.fillMaxWidth())
            // The thing most confirmation screens leave out. Something has just left the
            // device; the moment the owner is most likely to change their mind is now.
            OdoButton(
                text = deleteLabel,
                onClick = onDelete,
                variant = OdoButtonVariant.Tertiary,
            )
        }
    }
}

/* ------------------------------ Report ------------------------------ */

@Composable
internal fun ReportSentScreen(
    headline: String,
    intro: String,
    facts: List<Pair<String, String>>,
    waitLabel: String?,
    waitBody: String?,
    waitAction: String?,
    doneLabel: String,
    onWaitAction: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().systemBarsPadding()) {
        SentScaffold(headline = headline, intro = intro, modifier = Modifier.weight(1f)) {
            SentFactsCard(facts)
            // Only where there is something useful to do meanwhile. A "while you wait" card
            // with generic reassurance in it is a card nobody reads twice.
            if (waitLabel != null && waitBody != null && waitAction != null) {
                OdoCard {
                    OdoText(
                        text = waitLabel,
                        style = OdoTheme.typography.label,
                        color = OdoTheme.colors.textMuted,
                    )
                    OdoText(text = waitBody, style = OdoTheme.typography.body)
                    OdoButton(
                        text = waitAction,
                        onClick = onWaitAction,
                        variant = OdoButtonVariant.Tertiary,
                    )
                }
            }
        }
        OdoButton(
            text = doneLabel,
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(OdoTheme.spacing.screenEdge),
        )
    }
}

@OdoThemePreviews
@Composable
private fun DiagnosticsSentPreview() = OdoPreview(padded = false) {
    DiagnosticsSentScreen(
        reference = "DX-8F42-19",
        referenceLabel = "REFERENCE",
        headline = "Diagnostics sent",
        intro = "Quote this reference if you write in about the same problem.",
        facts = listOf(
            "Sent" to "Just now",
            "Size" to "240 KB",
            "Deleted by" to "5 Oct 2026",
        ),
        copyLabel = "Copy reference",
        deleteLabel = "Delete what I just sent",
        onCopy = {},
        onDelete = {},
    )
}

@OdoThemePreviews
@Composable
private fun ReportSentPreview() = OdoPreview(padded = false) {
    ReportSentScreen(
        headline = "Report sent",
        intro = "A real person reads this. You'll get an email at r•••@gmail.com.",
        facts = listOf(
            "Ticket" to "ODO-4821",
            "Area" to "Bill scan",
            "Attached" to "1 photo · app logs",
        ),
        waitLabel = "WHILE YOU WAIT",
        waitBody = "You can still log this bill by hand — the amount won't be lost.",
        waitAction = "Enter it manually",
        doneLabel = "Done",
        onWaitAction = {},
        onDone = {},
    )
}
