package com.hopcape.odo.feature.billcheck.presentation.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoEvidenceDots
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.core.domain.shared.formatRupeeRangeTo
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.billcheck.domain.BillCheck
import com.hopcape.odo.feature.billcheck.domain.Evidence
import com.hopcape.odo.feature.billcheck.domain.FlaggedLine
import com.hopcape.odo.feature.billcheck.domain.PricedLine
import com.hopcape.odo.feature.billcheck.domain.Reason
import com.hopcape.odo.feature.billcheck.resources.Res
import com.hopcape.odo.feature.billcheck.resources.bc_add_last_bill
import com.hopcape.odo.feature.billcheck.resources.bc_add_last_bill_tail
import com.hopcape.odo.feature.billcheck.resources.bc_ask_label
import com.hopcape.odo.feature.billcheck.resources.bc_ask_quote
import com.hopcape.odo.feature.billcheck.resources.bc_cd_back
import com.hopcape.odo.feature.billcheck.resources.bc_cd_line_ok
import com.hopcape.odo.feature.billcheck.resources.bc_context
import com.hopcape.odo.feature.billcheck.resources.bc_evidence_bills
import com.hopcape.odo.feature.billcheck.resources.bc_evidence_own
import com.hopcape.odo.feature.billcheck.resources.bc_evidence_rates
import com.hopcape.odo.feature.billcheck.resources.bc_headline
import com.hopcape.odo.feature.billcheck.resources.bc_how_we_know
import com.hopcape.odo.feature.billcheck.resources.bc_masked_amount
import com.hopcape.odo.feature.billcheck.resources.bc_masked_subhead
import com.hopcape.odo.feature.billcheck.resources.bc_reason_above
import com.hopcape.odo.feature.billcheck.resources.bc_reason_month
import com.hopcape.odo.feature.billcheck.resources.bc_reason_months
import com.hopcape.odo.feature.billcheck.resources.bc_reason_repeat
import com.hopcape.odo.feature.billcheck.resources.bc_reason_schedule
import com.hopcape.odo.feature.billcheck.resources.bc_retry
import com.hopcape.odo.feature.billcheck.resources.bc_share
import com.hopcape.odo.feature.billcheck.resources.bc_subhead
import com.hopcape.odo.feature.billcheck.resources.bc_subhead_none
import com.hopcape.odo.feature.billcheck.resources.bc_subhead_one
import com.hopcape.odo.feature.billcheck.resources.bc_title
import com.hopcape.odo.feature.billcheck.resources.bc_workshop_authorised
import com.hopcape.odo.feature.billcheck.resources.bc_workshop_local
import com.hopcape.odo.feature.billcheck.resources.bc_workshop_multi_brand
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The bill check result — what on this bill is worth asking about, and what to say.
 *
 * The flagged lines come first and the rest of the bill is still listed under them. Hiding
 * the lines that were priced fine would leave the owner unable to tell "we checked and these
 * were fine" from "we only looked at three".
 *
 * **Nothing here is a verdict.** A rate the reference table can defend carries its rupee
 * figure; a claim about the maker's schedule becomes a question instead, because with no
 * history the app cannot know whether this car needs the job (AI_ADVISORY_PLAN §2.8).
 *
 * Stateless: renders [state] and forwards [BillCheckEvent]s.
 */
@Composable
internal fun BillCheckScreen(
    state: BillCheckUiState,
    onEvent: (BillCheckEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.content
    val ready = content as? BillCheckUiState.Content.Ready

    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.bc_title),
        onBack = { onEvent(BillCheckEvent.BackClicked) },
        backContentDescription = stringResource(Res.string.bc_cd_back),
        // Nothing to share or explain while the answer is behind the wall.
        bottomBar = { if (ready != null && !ready.locked) Actions(ready.check, onEvent) },
    ) { padding ->
        when (content) {
            BillCheckUiState.Content.Loading -> Centred(padding) { OdoLoadingIndicator() }

            is BillCheckUiState.Content.Failed -> Centred(padding) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                ) {
                    OdoText(
                        text = content.message.asString(),
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                    OdoButton(
                        text = stringResource(Res.string.bc_retry),
                        onClick = { onEvent(BillCheckEvent.RetryClicked) },
                        variant = OdoButtonVariant.Secondary,
                    )
                }
            }

            is BillCheckUiState.Content.Ready -> Result(content, padding, onEvent)
        }
    }
}

@Composable
private fun Result(
    ready: BillCheckUiState.Content.Ready,
    padding: PaddingValues,
    onEvent: (BillCheckEvent) -> Unit,
) {
    val check = ready.check
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Header(check, ready.locked, onEvent)

        if (ready.locked) {
            // Names and the count, nothing else. Masked rather than blurred: the count is
            // what is being sold, and a blur says the same thing while looking like a fault.
            OdoCard(contentPadding = PaddingValues(0.dp)) {
                check.flagged.forEachIndexed { index, line ->
                    if (index > 0) OdoDivider()
                    MaskedRow(line.name) { onEvent(BillCheckEvent.UnlockClicked) }
                }
            }
        } else {
            OdoCard(contentPadding = PaddingValues(0.dp)) {
                check.flagged.forEachIndexed { index, line ->
                    if (index > 0) OdoDivider()
                    FlaggedRow(line, check.workshop)
                }
                check.fine.forEach { line ->
                    OdoDivider()
                    FineRow(line)
                }
            }
        }
    }
}

@Composable
private fun Header(check: BillCheck, locked: Boolean, onEvent: (BillCheckEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(
            text = stringResource(
                Res.string.bc_context,
                check.car,
                check.city,
                check.workshop.label(),
            ).uppercase(),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.textMuted,
        )
        OdoText(
            text = stringResource(
                Res.string.bc_headline,
                if (locked) stringResource(Res.string.bc_masked_amount)
                else check.worthAsking.formatRupees(),
            ),
            style = OdoTheme.typography.display,
            color = if (locked) OdoTheme.colors.textDim else OdoTheme.colors.text,
        )
        if (locked) {
            OdoText(
                text = stringResource(
                    Res.string.bc_masked_subhead,
                    check.flagged.size,
                    check.lineCount,
                ),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textMuted,
            )
        } else {
            Subhead(check, onEvent)
        }
    }
}

/**
 * The line under the headline, and — while there is no record to compare against — what
 * adding one would buy. Never an empty state: the check still found what it could.
 */
@Composable
private fun Subhead(check: BillCheck, onEvent: (BillCheckEvent) -> Unit) {
    val total = check.billTotal.formatRupees()
    val text = when {
        check.flagged.isEmpty() ->
            stringResource(Res.string.bc_subhead_none, total, check.lineCount)

        check.flagged.size == 1 ->
            stringResource(Res.string.bc_subhead_one, total, check.lineCount)

        else -> stringResource(Res.string.bc_subhead, total, check.flagged.size, check.lineCount)
    }
    val nudge = stringResource(Res.string.bc_add_last_bill)
    val tail = stringResource(Res.string.bc_add_last_bill_tail)

    if (check.canFlagRepeats) {
        OdoText(text = text, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
    } else {
        OdoText(
            text = buildAnnotatedString {
                append("$text ")
                withBold { append(nudge) }
                append(tail)
            },
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.clickable { onEvent(BillCheckEvent.AddLastBillClicked) },
        )
    }
}

/** One flagged line: what it is, what it cost, why it is here, and what to ask. */
@Composable
private fun FlaggedRow(line: FlaggedLine, workshop: WorkshopTier) {
    Column(
        modifier = Modifier.padding(OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(FLAG_DOT)
                    .background(OdoTheme.colors.warning, CircleShape),
            )
            OdoText(
                text = line.name,
                style = OdoTheme.typography.heading,
                modifier = Modifier.weight(1f),
            )
            OdoText(
                text = line.amount.formatRupees(),
                style = OdoTheme.typography.heading,
                // Tinted only where the rupee figure is part of the claim. A schedule
                // question is about the maker, not about this price, so its amount is drawn
                // as plainly as any other line's.
                color = if (line.amountIsTheClaim) OdoTheme.colors.warning else OdoTheme.colors.text,
            )
        }
        OdoText(text = line.reason.sentence(workshop), style = OdoTheme.typography.body)
        OdoEvidenceDots(filled = line.evidence.strength, label = line.evidence.label())
        line.ask?.let { AskCard(it) }
    }
}

/**
 * The question to put to the advisor.
 *
 * The whole feature exists to produce this. It is drawn as its own block rather than as
 * another sentence because it is the one thing the owner takes to the counter.
 */
@Composable
private fun AskCard(ask: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg, RoundedCornerShape(ASK_RADIUS))
            .padding(OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        OdoText(
            text = stringResource(Res.string.bc_ask_label),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.warning,
        )
        OdoText(
            text = stringResource(Res.string.bc_ask_quote, ask),
            style = OdoTheme.typography.heading,
        )
    }
}

/** A line with nothing to ask about. Listed, so "checked and fine" is visible. */
@Composable
private fun FineRow(line: PricedLine) {
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
        OdoIcon(
            imageVector = IcCheck,
            contentDescription = stringResource(Res.string.bc_cd_line_ok),
            tint = OdoTheme.colors.textMuted,
            size = CHECK_SIZE,
        )
        OdoText(
            text = line.name,
            style = OdoTheme.typography.body,
            modifier = Modifier.weight(1f),
        )
        OdoText(text = line.amount.formatRupees(), style = OdoTheme.typography.body)
    }
}

/** A flagged line with its finding withheld — the name sells, the number is what was bought. */
@Composable
private fun MaskedRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(OdoTheme.spacing.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(
            text = name,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .width(MASK_WIDTH)
                .height(MASK_HEIGHT)
                .background(OdoTheme.colors.border, RoundedCornerShape(MASK_RADIUS)),
        )
    }
}

@Composable
private fun Actions(check: BillCheck, onEvent: (BillCheckEvent) -> Unit) {
    val shareText = check.shareText()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(OdoTheme.spacing.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoButton(
            text = stringResource(Res.string.bc_share),
            onClick = { onEvent(BillCheckEvent.ShareClicked(shareText)) },
            modifier = Modifier.weight(1f),
        )
        OdoButton(
            text = stringResource(Res.string.bc_how_we_know),
            onClick = { onEvent(BillCheckEvent.HowWeKnowClicked) },
            modifier = Modifier.weight(1f),
            variant = OdoButtonVariant.Secondary,
        )
    }
}

/* ------------------------------ Copy assembly ------------------------------ */

/**
 * The reason sentence, with the values it turns on emphasised.
 *
 * The frame is copy and lives in `strings.xml`; the figures are the finding. Bolding is
 * applied by locating each substituted value in the formatted string rather than by writing
 * markup into the resource, so a translator moves the values around freely.
 */
@Composable
private fun Reason.sentence(workshop: WorkshopTier) = when (this) {
    is Reason.DoneRecently -> {
        val ago = if (monthsAgo <= 1) stringResource(Res.string.bc_reason_month)
        else stringResource(Res.string.bc_reason_months, monthsAgo)
        stringResource(Res.string.bc_reason_repeat, ago, on).emphasising(ago)
    }

    is Reason.AboveBand -> {
        val band = low.formatRupeeRangeTo(high)
        stringResource(Res.string.bc_reason_above, band, workshop.label())
            .emphasising(band)
    }

    is Reason.ScheduledLater -> {
        val distance = LocalOdoDistanceFormat.current
        val due = distance.format(dueAtKm)
        val now = distance.format(currentKm)
        stringResource(Res.string.bc_reason_schedule, due, now).emphasising(due, now)
    }
}

@Composable
private fun Evidence.label(): String = when (this) {
    Evidence.OwnRecord -> stringResource(Res.string.bc_evidence_own)
    is Evidence.RealBills -> stringResource(Res.string.bc_evidence_bills, count)
    Evidence.CityRates -> stringResource(Res.string.bc_evidence_rates)
}

@Composable
internal fun WorkshopTier.label(): String = when (this) {
    WorkshopTier.AUTHORISED -> stringResource(Res.string.bc_workshop_authorised)
    WorkshopTier.MULTI_BRAND -> stringResource(Res.string.bc_workshop_multi_brand)
    WorkshopTier.LOCAL -> stringResource(Res.string.bc_workshop_local)
}

/** Bold every [values] occurrence, searching forward so a repeated value matches once each. */
private fun String.emphasising(vararg values: String) = buildAnnotatedString {
    append(this@emphasising)
    var from = 0
    values.forEach { value ->
        if (value.isEmpty()) return@forEach
        val start = indexOf(value, from)
        if (start < 0) return@forEach
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + value.length)
        from = start + value.length
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withBold(block: () -> Unit) {
    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
    block()
    pop()
}

/** The share text. The plate and the workshop name are deliberately not in it. */
@Composable
private fun BillCheck.shareText(): String = stringResource(
    Res.string.bc_headline,
    worthAsking.formatRupees(),
)

/* ------------------------------ Layout tokens ------------------------------ */

private val FLAG_DOT = 8.dp
private val CHECK_SIZE = 18.dp
private val ASK_RADIUS = 12.dp
private val MASK_WIDTH = 64.dp
private val MASK_HEIGHT = 12.dp
private val MASK_RADIUS = 6.dp

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
private fun BillCheckPreview() = OdoPreview(padded = false) {
    BillCheckScreen(
        state = BillCheckUiState(
            BillCheckUiState.Content.Ready(BillCheckPreviewData.monthSix, locked = false),
        ),
        onEvent = {},
    )
}

/** Day 1: no record yet, so no repeat can be flagged and the screen says what that costs. */
@OdoThemePreviews
@Composable
private fun BillCheckDayOnePreview() = OdoPreview(padded = false) {
    BillCheckScreen(
        state = BillCheckUiState(
            BillCheckUiState.Content.Ready(BillCheckPreviewData.dayOne, locked = false),
        ),
        onEvent = {},
    )
}

@OdoThemePreviews
@Composable
private fun BillCheckLockedPreview() = OdoPreview(padded = false) {
    BillCheckScreen(
        state = BillCheckUiState(
            BillCheckUiState.Content.Ready(BillCheckPreviewData.dayOne, locked = true),
        ),
        onEvent = {},
    )
}
