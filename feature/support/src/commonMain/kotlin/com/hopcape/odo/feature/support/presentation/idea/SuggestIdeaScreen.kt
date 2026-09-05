package com.hopcape.odo.feature.support.presentation.idea

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.presentation.SectionLabel
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_idea_already
import com.hopcape.odo.feature.support.resources.sp_idea_hint
import com.hopcape.odo.feature.support.resources.sp_idea_label
import com.hopcape.odo.feature.support.resources.sp_idea_no_dates
import com.hopcape.odo.feature.support.resources.sp_idea_send
import com.hopcape.odo.feature.support.resources.sp_idea_status_in_progress
import com.hopcape.odo.feature.support.resources.sp_idea_status_shipped
import com.hopcape.odo.feature.support.resources.sp_idea_status_shipping
import com.hopcape.odo.feature.support.resources.sp_idea_status_under_review
import com.hopcape.odo.feature.support.resources.sp_idea_title
import com.hopcape.odo.feature.support.resources.sp_idea_vote_action
import com.hopcape.odo.feature.support.resources.sp_idea_voted
import com.hopcape.odo.feature.support.resources.sp_idea_votes
import org.jetbrains.compose.resources.stringResource

/**
 * An idea box, and the ideas already in it.
 *
 * The list is the half that makes this worth opening twice. Writing into a box and pressing
 * send tells the owner nothing about whether anyone read the last one; a row saying 412 people
 * asked for the same thing, and that it is in progress, answers that before they type.
 *
 * **No dates are promised anywhere on it.** A status is what is true now; a date is a
 * commitment the screen cannot keep, and one missed date costs more than the list is worth.
 */
@Composable
internal fun SuggestIdeaScreen(
    state: IdeaUiState,
    onEvent: (IdeaEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.sp_idea_title),
        onBack = { onEvent(IdeaEvent.BackClicked) },
        bottomBar = {
            OdoButton(
                text = stringResource(Res.string.sp_idea_send),
                onClick = { onEvent(IdeaEvent.SendClicked) },
                enabled = state.canSend,
                loading = state.sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OdoTheme.spacing.screenEdge),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            SectionLabel(stringResource(Res.string.sp_idea_label))
            OdoInputField(
                value = state.text,
                onValueChange = { onEvent(IdeaEvent.TextChanged(it)) },
                placeholder = stringResource(Res.string.sp_idea_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = IDEA_MIN_HEIGHT),
            )

            // Nothing to vote on yet is a section with nothing in it. Left out rather than
            // shown empty — an empty "already asked for" reads as a broken list.
            if (state.ideas.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.sp_idea_already))
                OdoCard(
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    state.ideas.forEachIndexed { index, idea ->
                        if (index > 0) OdoDivider()
                        IdeaRowItem(idea = idea, onEvent = onEvent)
                    }
                }
                OdoText(
                    text = stringResource(Res.string.sp_idea_no_dates),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun IdeaRowItem(idea: IdeaRow, onEvent: (IdeaEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OdoTheme.spacing.cardPadding,
                vertical = OdoTheme.spacing.md,
            ),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            OdoText(text = idea.title, style = OdoTheme.typography.body)
            OdoText(
                text = idea.status.label(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        VotePill(idea = idea, onClick = { onEvent(IdeaEvent.VoteToggled(idea.id)) })
    }
}

/**
 * The count and the act of voting, in one control.
 *
 * A separate button beside a number would be two things to read where the owner is doing one
 * thing. Voted turns the pill solid and its caption to "voted", so the state is legible
 * without colour alone carrying it.
 */
@Composable
private fun VotePill(idea: IdeaRow, onClick: () -> Unit) {
    // Which idea it votes for. The pill draws a number and a word, and neither says what
    // pressing it does — the title is in the row beside it, which a screen reader reads apart.
    val action = stringResource(Res.string.sp_idea_vote_action, idea.title)
    OdoCard(
        onClick = onClick,
        color = if (idea.voted) OdoTheme.colors.text else OdoTheme.colors.surface,
        contentColor = if (idea.voted) OdoTheme.colors.bg else OdoTheme.colors.text,
        contentPadding = PaddingValues(
            horizontal = OdoTheme.spacing.md,
            vertical = OdoTheme.spacing.sm,
        ),
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .widthIn(min = PILL_MIN_WIDTH)
            .semantics { contentDescription = action },
    ) {
        OdoText(
            text = idea.votes.toString(),
            style = OdoTheme.typography.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OdoText(
            text = if (idea.voted) {
                stringResource(Res.string.sp_idea_voted)
            } else {
                stringResource(Res.string.sp_idea_votes)
            },
            style = OdoTheme.typography.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IdeaStatus.label(): String = stringResource(
    when (this) {
        IdeaStatus.UNDER_REVIEW -> Res.string.sp_idea_status_under_review
        IdeaStatus.IN_PROGRESS -> Res.string.sp_idea_status_in_progress
        IdeaStatus.SHIPPING -> Res.string.sp_idea_status_shipping
        IdeaStatus.SHIPPED -> Res.string.sp_idea_status_shipped
    },
)

private val IDEA_MIN_HEIGHT = 132.dp
private val PILL_MIN_WIDTH = 72.dp

@OdoThemePreviews
@Composable
private fun SuggestIdeaPreview() = OdoPreview(padded = false) {
    SuggestIdeaScreen(
        state = IdeaUiState(
            ideas = listOf(
                IdeaRow("1", "Two cars on one account", IdeaStatus.IN_PROGRESS, 412, false),
                IdeaRow("2", "Export costs to Excel", IdeaStatus.UNDER_REVIEW, 288, false),
                IdeaRow("3", "Hindi interface", IdeaStatus.UNDER_REVIEW, 231, true),
                IdeaRow("4", "Insurance renewal reminders", IdeaStatus.SHIPPING, 195, false),
            ),
        ),
        onEvent = {},
    )
}
