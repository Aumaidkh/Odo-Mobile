package com.hopcape.odo.feature.healthscore.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoHealthDial
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoProgressBar
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcCaretUpFilled
import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.icons.IcGearFilled
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcLightningFilled
import com.hopcape.odo.core.designsystem.icons.IcLockFilled
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.feature.healthscore.presentation.state.Loadable
import com.hopcape.odo.feature.healthscore.resources.Res
import com.hopcape.odo.feature.healthscore.resources.hs_band_excellent
import com.hopcape.odo.feature.healthscore.resources.hs_band_fair
import com.hopcape.odo.feature.healthscore.resources.hs_band_good
import com.hopcape.odo.feature.healthscore.resources.hs_band_needs_care
import com.hopcape.odo.feature.healthscore.resources.hs_breakdown_label
import com.hopcape.odo.feature.healthscore.resources.hs_cd_back
import com.hopcape.odo.feature.healthscore.resources.hs_cd_info
import com.hopcape.odo.feature.healthscore.resources.hs_delta_down
import com.hopcape.odo.feature.healthscore.resources.hs_delta_none
import com.hopcape.odo.feature.healthscore.resources.hs_delta_up
import com.hopcape.odo.feature.healthscore.resources.hs_factor_cost_sub
import com.hopcape.odo.feature.healthscore.resources.hs_factor_cost_title
import com.hopcape.odo.feature.healthscore.resources.hs_factor_documentation_sub
import com.hopcape.odo.feature.healthscore.resources.hs_factor_documentation_title
import com.hopcape.odo.feature.healthscore.resources.hs_factor_history_sub
import com.hopcape.odo.feature.healthscore.resources.hs_factor_history_title
import com.hopcape.odo.feature.healthscore.resources.hs_factor_maintenance_sub
import com.hopcape.odo.feature.healthscore.resources.hs_factor_maintenance_title
import com.hopcape.odo.feature.healthscore.resources.hs_factor_score
import com.hopcape.odo.feature.healthscore.resources.hs_info_band_excellent
import com.hopcape.odo.feature.healthscore.resources.hs_info_band_fair
import com.hopcape.odo.feature.healthscore.resources.hs_info_band_good
import com.hopcape.odo.feature.healthscore.resources.hs_info_band_needs_care
import com.hopcape.odo.feature.healthscore.resources.hs_info_body
import com.hopcape.odo.feature.healthscore.resources.hs_info_cta
import com.hopcape.odo.feature.healthscore.resources.hs_info_footer
import com.hopcape.odo.feature.healthscore.resources.hs_info_points
import com.hopcape.odo.feature.healthscore.resources.hs_info_range_excellent
import com.hopcape.odo.feature.healthscore.resources.hs_info_range_fair
import com.hopcape.odo.feature.healthscore.resources.hs_info_range_good
import com.hopcape.odo.feature.healthscore.resources.hs_info_range_needs_care
import com.hopcape.odo.feature.healthscore.resources.hs_info_title
import com.hopcape.odo.feature.healthscore.resources.hs_nothing_logged
import com.hopcape.odo.feature.healthscore.resources.hs_opportunity_cost
import com.hopcape.odo.feature.healthscore.resources.hs_opportunity_documents
import com.hopcape.odo.feature.healthscore.resources.hs_opportunity_history
import com.hopcape.odo.feature.healthscore.resources.hs_opportunity_label
import com.hopcape.odo.feature.healthscore.resources.hs_opportunity_maintenance
import com.hopcape.odo.feature.healthscore.resources.hs_out_of
import com.hopcape.odo.feature.healthscore.resources.hs_paywall_body
import com.hopcape.odo.feature.healthscore.resources.hs_paywall_cta
import com.hopcape.odo.feature.healthscore.resources.hs_paywall_title
import com.hopcape.odo.feature.healthscore.resources.hs_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Health Score detail — a 0–100 dial with its band, what the score has done this month,
 * and the four-factor breakdown. Pro shows every factor plus the biggest-opportunity nudge;
 * free sees the first factor peek behind a paywall.
 *
 * State-free: renders [state] and forwards intents. Everything shown is decided in the
 * ViewModel, including which of the three things to say under the dial.
 */
@Composable
internal fun HealthScoreScreen(
    state: HealthScoreUiState,
    onEvent: (HealthScoreEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = { HealthTopBar(onEvent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            when (val content = state.content) {
                is Loadable.Loading -> Unit
                is Loadable.Failed -> FailedMessage(content)
                is Loadable.Ready -> ScoreContent(content.value, onEvent)
            }
        }
    }
}

@Composable
private fun ScoreContent(content: HealthScoreContent, onEvent: (HealthScoreEvent) -> Unit) {
    DialBlock(content.score, content.band)
    NoteLine(content.note)

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        SectionLabel(stringResource(Res.string.hs_breakdown_label))
        if (content.entitlements.has(ProFeature.HEALTH_BREAKDOWN)) {
            content.factors.forEach { FactorCard(it) }
            content.opportunity?.let { OpportunityCard(it) }
        } else {
            LockedBreakdown(content.factors) { onEvent(HealthScoreEvent.UnlockTapped) }
        }
    }
}

/**
 * The read failed, so there is no number. Said plainly instead of showing a zero — a car
 * with a year of history does not deserve to be told it scores nothing because a query
 * broke.
 */
@Composable
private fun FailedMessage(failed: Loadable.Failed) {
    OdoText(
        failed.message.asString(),
        style = OdoTheme.typography.body,
        color = OdoTheme.colors.textDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.screenEdge),
    )
}

@Composable
private fun HealthTopBar(onEvent: (HealthScoreEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoCircularIconButton(
            IcArrowLeft,
            contentDescription = stringResource(Res.string.hs_cd_back),
            onClick = { onEvent(HealthScoreEvent.BackTapped) },
            variant = OdoCircularIconButtonVariant.Outlined,
        )
        OdoText(stringResource(Res.string.hs_title), style = OdoTheme.typography.title)
        Box(Modifier.weight(1f))
        OdoCircularIconButton(
            IcInfo,
            contentDescription = stringResource(Res.string.hs_cd_info),
            onClick = { onEvent(HealthScoreEvent.InfoTapped) },
            variant = OdoCircularIconButtonVariant.Outlined,
        )
    }
}

@Composable
private fun DialBlock(score: Int, band: HealthBand) {
    val color = bandColor(band)
    OdoHealthDial(score = score, arcColor = color, dialSize = 200.dp) {
        OdoText(
            score.toString(),
            style = OdoTheme.typography.display,
            color = OdoTheme.colors.text,
            modifier = Modifier.testTag(HealthScoreTestTags.SCORE),
        )
        OdoText(stringResource(Res.string.hs_out_of), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        OdoText(bandLabel(band), style = OdoTheme.typography.label, color = color)
    }
}

/**
 * The line under the dial. A car with nothing logged gets a way forward rather than a
 * movement, and a car with no score from a month ago gets nothing at all.
 */
@Composable
private fun NoteLine(note: HealthNote) {
    when (note) {
        HealthNote.NoHistoryYet -> Unit

        HealthNote.NothingLoggedYet -> OdoText(
            stringResource(Res.string.hs_nothing_logged),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OdoTheme.spacing.screenEdge)
                .testTag(HealthScoreTestTags.NOTE),
        )

        is HealthNote.Delta -> DeltaLine(note.points)
    }
}

@Composable
private fun DeltaLine(points: Int) {
    val up = points >= 0
    val color = when {
        points == 0 -> OdoTheme.colors.textDim
        up -> OdoTheme.colors.success
        else -> OdoTheme.colors.danger
    }
    val text = when {
        points == 0 -> stringResource(Res.string.hs_delta_none)
        up -> stringResource(Res.string.hs_delta_up, points)
        else -> stringResource(Res.string.hs_delta_down, -points)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag(HealthScoreTestTags.NOTE),
    ) {
        if (points != 0) {
            OdoIcon(
                IcCaretUpFilled,
                contentDescription = null,
                tint = color,
                size = OdoTheme.iconSizes.small,
                modifier = if (up) Modifier else Modifier.rotate(180f),
            )
        }
        OdoText(text, style = OdoTheme.typography.heading, color = color)
    }
}

@Composable
private fun FactorCard(factor: HealthFactor) {
    val color = factorColor(factor.fraction)
    OdoCard(modifier = Modifier.testTag(HealthScoreTestTags.factorRow(factor.kind))) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                IconChip(factorIcon(factor.kind), color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                    OdoText(factorTitle(factor.kind), style = OdoTheme.typography.heading)
                    OdoText(factorSubtitle(factor.kind), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                }
                OdoText(
                    stringResource(Res.string.hs_factor_score, factor.earned, factor.max),
                    style = OdoTheme.typography.heading,
                )
            }
            OdoProgressBar(progress = factor.fraction, color = color)
        }
    }
}

/** The single highest-impact next step: the factor with the most points still to earn. */
@Composable
private fun OpportunityCard(opportunity: HealthFactor) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.testTag(HealthScoreTestTags.OPPORTUNITY),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(IcLightningFilled, accent)
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(
                    stringResource(Res.string.hs_opportunity_label, opportunity.missing),
                    style = OdoTheme.typography.caption,
                    color = accent,
                )
                OdoText(
                    opportunityMessage(opportunity.kind),
                    style = OdoTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** Free: the first factor peeks, then the rest fade behind a scrim with the Pro paywall. */
@Composable
private fun LockedBreakdown(factors: List<HealthFactor>, onUnlock: () -> Unit) {
    factors.firstOrNull()?.let { FactorCard(it) }
    Box(Modifier.fillMaxWidth().height(300.dp).testTag(HealthScoreTestTags.PAYWALL)) {
        Column(
            Modifier.fillMaxWidth().alpha(0.18f),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            factors.drop(1).forEach { FactorCard(it) }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(OdoTheme.colors.bg.copy(alpha = 0.5f), OdoTheme.colors.bg)),
            ),
        )
        Column(
            modifier = Modifier.matchParentSize().padding(horizontal = OdoTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(56.dp).clip(OdoTheme.shapes.card).background(OdoTheme.colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcLockFilled, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.large)
            }
            OdoText(stringResource(Res.string.hs_paywall_title), style = OdoTheme.typography.heading, color = OdoTheme.colors.text)
            OdoText(
                stringResource(Res.string.hs_paywall_body),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
            OdoButton(stringResource(Res.string.hs_paywall_cta), onClick = onUnlock)
        }
    }
}

/**
 * "How your score works" — the content of the info bottom sheet opened from the (i) button.
 * Explains the 100-point split across the four factors and the band cut-offs. The
 * [ModalBottomSheet] chrome is supplied by the navigation layer (the sheet is a real
 * destination — see `ModalBottomSheetSceneStrategy`); this renders only the sheet body.
 * [onDismiss] pops the sheet destination.
 *
 * It scrolls: four weights and four bands are taller than a short phone's sheet, and the
 * bottom of this one is the button that closes it.
 */
@Composable
internal fun HowScoreWorksContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoText(stringResource(Res.string.hs_info_title), style = OdoTheme.typography.title)
            OdoText(stringResource(Res.string.hs_info_body), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            HealthFactorKind.entries.forEach { kind ->
                WeightRow(factorIcon(kind), factorTitle(kind), kind.weight)
            }
        }

        OdoCard(
            contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            HealthBand.entries.reversed().forEachIndexed { index, band ->
                if (index > 0) HorizontalDivider(color = OdoTheme.colors.border)
                BandRow(bandColor(band), infoBandLabel(band), infoBandRange(band))
            }
        }

        OdoText(
            stringResource(Res.string.hs_info_footer),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.lg),
        )
        OdoButton(stringResource(Res.string.hs_info_cta), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WeightRow(icon: ImageVector, title: String, points: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconChip(icon, OdoTheme.colors.accent)
        OdoText(title, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
        OdoText(stringResource(Res.string.hs_info_points, points), style = OdoTheme.typography.heading)
    }
}

@Composable
private fun BandRow(dotColor: Color, label: String, range: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        OdoText(
            label,
            style = OdoTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        OdoText(range, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
    }
}

@Composable
private fun IconChip(icon: ImageVector, tone: Color) {
    Box(
        Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(tone.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tone, size = OdoTheme.iconSizes.medium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    OdoText(text, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
}

/**
 * The band's colour. Two bands are green, because both are places an owner can be happy to
 * be; only [HealthBand.POOR] is red, and [HealthBand.FAIR] is the amber in between. The
 * same three steps the design system's `healthScoreColor` uses, so the dial's arc and the
 * label under it can never disagree.
 */
@Composable
private fun bandColor(band: HealthBand): Color = when (band) {
    HealthBand.POOR -> OdoTheme.colors.danger
    HealthBand.FAIR -> OdoTheme.colors.warning
    HealthBand.GOOD, HealthBand.EXCELLENT -> OdoTheme.colors.success
}

@Composable
private fun factorColor(fraction: Float): Color = when {
    fraction >= 0.75f -> OdoTheme.colors.success
    fraction >= 0.5f -> OdoTheme.colors.warning
    else -> OdoTheme.colors.danger
}

@Composable
private fun bandLabel(band: HealthBand): String = stringResource(
    when (band) {
        HealthBand.POOR -> Res.string.hs_band_needs_care
        HealthBand.FAIR -> Res.string.hs_band_fair
        HealthBand.GOOD -> Res.string.hs_band_good
        HealthBand.EXCELLENT -> Res.string.hs_band_excellent
    },
)

@Composable
private fun infoBandLabel(band: HealthBand): String = stringResource(
    when (band) {
        HealthBand.POOR -> Res.string.hs_info_band_needs_care
        HealthBand.FAIR -> Res.string.hs_info_band_fair
        HealthBand.GOOD -> Res.string.hs_info_band_good
        HealthBand.EXCELLENT -> Res.string.hs_info_band_excellent
    },
)

@Composable
private fun infoBandRange(band: HealthBand): String = stringResource(
    when (band) {
        HealthBand.POOR -> Res.string.hs_info_range_needs_care
        HealthBand.FAIR -> Res.string.hs_info_range_fair
        HealthBand.GOOD -> Res.string.hs_info_range_good
        HealthBand.EXCELLENT -> Res.string.hs_info_range_excellent
    },
)

private fun factorIcon(kind: HealthFactorKind): ImageVector = when (kind) {
    HealthFactorKind.MAINTENANCE -> IcGearFilled
    HealthFactorKind.DOCUMENTATION -> IcShieldCheck
    HealthFactorKind.COST_EFFICIENCY -> IcCurrencyDollar
    HealthFactorKind.HISTORY -> IcSpeedometer
}

@Composable
private fun factorTitle(kind: HealthFactorKind): String = stringResource(
    when (kind) {
        HealthFactorKind.MAINTENANCE -> Res.string.hs_factor_maintenance_title
        HealthFactorKind.DOCUMENTATION -> Res.string.hs_factor_documentation_title
        HealthFactorKind.COST_EFFICIENCY -> Res.string.hs_factor_cost_title
        HealthFactorKind.HISTORY -> Res.string.hs_factor_history_title
    },
)

@Composable
private fun factorSubtitle(kind: HealthFactorKind): String = stringResource(
    when (kind) {
        HealthFactorKind.MAINTENANCE -> Res.string.hs_factor_maintenance_sub
        HealthFactorKind.DOCUMENTATION -> Res.string.hs_factor_documentation_sub
        HealthFactorKind.COST_EFFICIENCY -> Res.string.hs_factor_cost_sub
        HealthFactorKind.HISTORY -> Res.string.hs_factor_history_sub
    },
)

@Composable
private fun opportunityMessage(kind: HealthFactorKind): String = stringResource(
    when (kind) {
        HealthFactorKind.MAINTENANCE -> Res.string.hs_opportunity_maintenance
        HealthFactorKind.DOCUMENTATION -> Res.string.hs_opportunity_documents
        HealthFactorKind.COST_EFFICIENCY -> Res.string.hs_opportunity_cost
        HealthFactorKind.HISTORY -> Res.string.hs_opportunity_history
    },
)

@OdoThemePreviews
@Composable
private fun HealthScoreGoodProPreview() = OdoPreview(padded = false) {
    HealthScoreScreen(previewHealthGood(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun HealthScoreNeedsCareProPreview() = OdoPreview(padded = false) {
    HealthScoreScreen(previewHealthNeedsCare(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun HealthScoreExcellentFreePreview() = OdoPreview(padded = false) {
    HealthScoreScreen(previewHealthExcellent(isPro = false), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun HealthScoreNothingLoggedPreview() = OdoPreview(padded = false) {
    HealthScoreScreen(previewHealthNothingLogged(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun HowScoreWorksContentPreview() = OdoPreview {
    HowScoreWorksContent(onDismiss = {})
}
