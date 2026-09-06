package com.hopcape.odo.feature.advisory.presentation.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcMinus
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.shared.formatRupeeRangeTo
import com.hopcape.odo.core.platform.share.toPngBytes
import com.hopcape.odo.feature.advisory.domain.checklist.ChecklistItem
import com.hopcape.odo.feature.advisory.domain.checklist.ServiceChecklist
import com.hopcape.odo.feature.advisory.resources.Res
import com.hopcape.odo.feature.advisory.resources.adv_check_cd_back
import com.hopcape.odo.feature.advisory.resources.adv_check_cost_label
import com.hopcape.odo.feature.advisory.resources.adv_check_cost_partial
import com.hopcape.odo.feature.advisory.resources.adv_check_empty_body
import com.hopcape.odo.feature.advisory.resources.adv_check_empty_title
import com.hopcape.odo.feature.advisory.resources.adv_check_headline
import com.hopcape.odo.feature.advisory.resources.adv_check_headline_clear
import com.hopcape.odo.feature.advisory.resources.adv_check_unavailable_body
import com.hopcape.odo.feature.advisory.resources.adv_check_not_needed
import com.hopcape.odo.feature.advisory.resources.adv_check_question_1
import com.hopcape.odo.feature.advisory.resources.adv_check_question_2
import com.hopcape.odo.feature.advisory.resources.adv_check_question_3
import com.hopcape.odo.feature.advisory.resources.adv_check_questions
import com.hopcape.odo.feature.advisory.resources.adv_check_save
import com.hopcape.odo.feature.advisory.resources.adv_check_separator
import com.hopcape.odo.feature.advisory.resources.adv_check_title
import com.hopcape.odo.feature.advisory.resources.adv_check_years
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Before you go in" — the screen an owner reads in the car park.
 *
 * Two lists and a price, in that order, because that is the order the conversation at the
 * counter happens in. The three questions at the bottom are the whole liability model: the
 * screen hands over questions, never a diagnosis, so nothing here says a job is unnecessary.
 *
 * The card is drawn once and captured from that same drawing, so what lands in the owner's
 * downloads is what they were shown. The recording sits on the card itself rather than the
 * scroll container, which is what makes a list taller than the screen save whole.
 *
 * Stateless: renders [state] and forwards [ChecklistEvent]s.
 */
@Composable
internal fun ChecklistScreen(
    state: ChecklistUiState,
    onEvent: (ChecklistEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** Where "saved" and "could not save" are said. */
    snackbarHostState: SnackbarHostState? = null,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.adv_check_title),
        onBack = { onEvent(ChecklistEvent.BackClicked) },
        backContentDescription = stringResource(Res.string.adv_check_cd_back),
        snackbarHostState = snackbarHostState,
        bottomBar = {
            if (!state.isLoading && !state.isEmpty) {
                SaveButton(enabled = !state.saving) {
                    scope.launch {
                        onEvent(ChecklistEvent.SaveClicked(layer.toImageBitmap().toPngBytes()))
                    }
                }
            }
        },
    ) { padding ->
        val checklist = state.checklist
        when {
            state.isLoading -> Centred(padding) { OdoLoadingIndicator() }

            state.isEmpty || checklist == null -> Centred(padding) {
                OdoEmptyState(
                    title = stringResource(Res.string.adv_check_empty_title),
                    // A schedule that could not be read is not a car with nothing due, and
                    // telling an owner "nothing to prepare for" during a reference-data
                    // outage is the one wrong thing this screen can say.
                    message = stringResource(
                        if (checklist?.scheduleUnavailable == true) {
                            Res.string.adv_check_unavailable_body
                        } else {
                            Res.string.adv_check_empty_body
                        },
                    ),
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                Card(checklist, layer)
            }
        }
    }
}

/**
 * Everything that gets saved.
 *
 * [layer] records this composable's own drawing, whose size is the card's full measured
 * height rather than the viewport — so a list that scrolls still saves in one piece.
 *
 * **Opaque, and inside the recording.** Only the inner cards paint a fill of their own; the
 * eyebrow, the headline and the gaps between cards draw straight onto the screen's own
 * ground. Recorded without one, they come out transparent, and the saved file opened over
 * anything light — a gallery, a chat, a print — is two grey boxes under an invisible
 * heading. It is the theme's ground rather than a fixed colour on purpose: this file is
 * saved for the owner to hold up at a counter on the phone they saved it from, not sent to
 * someone else's, which is what `ShareCardScreen` fixes its colours for.
 */
@Composable
private fun Card(checklist: ServiceChecklist, layer: GraphicsLayer) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .background(OdoTheme.colors.bg)
            .padding(vertical = OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Header(checklist, nothingDue = checklist.checklist.due.isEmpty())
        if (checklist.checklist.due.isNotEmpty()) {
            OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                checklist.checklist.due.forEachIndexed { index, item ->
                    if (index > 0) OdoDivider()
                    ItemRow(item, IcCheck, OdoTheme.colors.text)
                }
            }
        }
        if (checklist.checklist.notYet.isNotEmpty()) {
            OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                OdoText(
                    text = stringResource(Res.string.adv_check_not_needed),
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.textMuted,
                )
                checklist.checklist.notYet.forEach { item ->
                    OdoDivider()
                    ItemRow(item, IcMinus, OdoTheme.colors.textDim, trailingSubtitle = true)
                }
            }
        }
        checklist.cost?.let { Cost(it) }
        Questions()
    }
}

/**
 * "i20 · 3 years · 42,000 km", then the headline the whole screen answers.
 *
 * [nothingDue] swaps the headline rather than the screen. A car with nothing due still needs
 * the section below it — the anti-upsell list and the three questions are the half that
 * matters most to an owner an advisor is about to sell something to.
 */
@Composable
private fun Header(checklist: ServiceChecklist, nothingDue: Boolean) {
    val distance = LocalOdoDistanceFormat.current
    val separator = stringResource(Res.string.adv_check_separator)
    val years = checklist.ageYears?.let { pluralStringResource(Res.plurals.adv_check_years, it, it) }
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(
            text = listOfNotNull(checklist.carName, years, distance.format(checklist.odometerKm))
                .joinToString(separator)
                .uppercase(),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.textMuted,
        )
        OdoText(
            text = stringResource(
                if (nothingDue) Res.string.adv_check_headline_clear else Res.string.adv_check_headline,
            ),
            style = OdoTheme.typography.display,
        )
    }
}

/**
 * One job.
 *
 * A not-due row puts its number on the right rather than under the name: the eye is
 * scanning for "how long have I got", and the left column is for what is being insisted on.
 */
@Composable
private fun ItemRow(
    item: ChecklistItem,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    trailingSubtitle: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.small)
        Column(modifier = Modifier.weight(1f)) {
            OdoText(item.title(), style = OdoTheme.typography.heading, color = tint)
            if (!trailingSubtitle) {
                OdoText(
                    text = item.subtitle(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
        if (trailingSubtitle) {
            OdoText(
                text = item.subtitle(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
    }
}

/**
 * What the due work should cost.
 *
 * The "covers 3 of 5 jobs" line appears whenever the tables could not price everything. It
 * is the difference between a figure an owner can hold a workshop to and one that quietly
 * left two jobs out.
 */
@Composable
private fun Cost(cost: com.hopcape.odo.feature.advisory.domain.checklist.ChecklistCost) {
    OdoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(stringResource(Res.string.adv_check_cost_label), style = OdoTheme.typography.body)
            OdoText(
                // The unit stated once, which is what a band reads as. Two formatted
                // amounts side by side read as two prices.
                text = cost.range.low.formatRupeeRangeTo(cost.range.high),
                style = OdoTheme.typography.heading,
            )
        }
        if (cost.pricedItems < cost.dueItems) {
            OdoText(
                text = stringResource(Res.string.adv_check_cost_partial, cost.pricedItems, cost.dueItems),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
    }
}

/** Three fixed questions. Not a diagnosis, and never generated. */
@Composable
private fun Questions() {
    OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoText(
            text = stringResource(Res.string.adv_check_questions),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.warning,
        )
        listOf(
            Res.string.adv_check_question_1,
            Res.string.adv_check_question_2,
            Res.string.adv_check_question_3,
        ).forEachIndexed { index, question ->
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                OdoText(
                    text = "${index + 1}",
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textMuted,
                )
                OdoText(stringResource(question), style = OdoTheme.typography.body)
            }
        }
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    OdoButton(
        text = stringResource(Res.string.adv_check_save),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(OdoTheme.spacing.screenEdge),
    )
}

@Composable
private fun Centred(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@OdoThemePreviews
@Composable
private fun ChecklistScreenPreview() = OdoPreview {
    ChecklistScreen(state = ChecklistUiState(isLoading = false, checklist = previewChecklist), onEvent = {})
}
