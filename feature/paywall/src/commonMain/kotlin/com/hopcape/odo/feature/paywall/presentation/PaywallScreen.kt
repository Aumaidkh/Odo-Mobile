package com.hopcape.odo.feature.paywall.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.icons.IcStarFilled
import com.hopcape.odo.core.designsystem.icons.IcTagFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_badge_generic
import com.hopcape.odo.feature.paywall.resources.pw_badge_savings
import com.hopcape.odo.feature.paywall.resources.pw_badge_scans
import com.hopcape.odo.feature.paywall.resources.pw_cd_close
import com.hopcape.odo.feature.paywall.resources.pw_cta
import com.hopcape.odo.feature.paywall.resources.pw_cta_trial
import com.hopcape.odo.feature.paywall.resources.pw_cta_working
import com.hopcape.odo.feature.paywall.resources.pw_feature_documents_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_documents_title
import com.hopcape.odo.feature.paywall.resources.pw_feature_export_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_export_title
import com.hopcape.odo.feature.paywall.resources.pw_feature_health_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_health_title
import com.hopcape.odo.feature.paywall.resources.pw_feature_scans_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_scans_title
import com.hopcape.odo.feature.paywall.resources.pw_footer
import com.hopcape.odo.feature.paywall.resources.pw_headline_generic
import com.hopcape.odo.feature.paywall.resources.pw_headline_savings
import com.hopcape.odo.feature.paywall.resources.pw_headline_scans
import com.hopcape.odo.feature.paywall.resources.pw_period_annual
import com.hopcape.odo.feature.paywall.resources.pw_terms_lifetime
import com.hopcape.odo.feature.paywall.resources.pw_plan_lifetime
import com.hopcape.odo.feature.paywall.resources.pw_plan_lifetime_period
import com.hopcape.odo.feature.paywall.resources.pw_period_monthly
import com.hopcape.odo.feature.paywall.resources.pw_plan_annual
import com.hopcape.odo.feature.paywall.resources.pw_plan_annual_badge
import com.hopcape.odo.feature.paywall.resources.pw_plan_annual_period
import com.hopcape.odo.feature.paywall.resources.pw_plan_monthly
import com.hopcape.odo.feature.paywall.resources.pw_plan_monthly_period
import com.hopcape.odo.feature.paywall.resources.pw_restore
import com.hopcape.odo.feature.paywall.resources.pw_retry
import com.hopcape.odo.feature.paywall.resources.pw_subtitle_generic
import com.hopcape.odo.feature.paywall.resources.pw_subtitle_savings
import com.hopcape.odo.feature.paywall.resources.pw_subtitle_scans
import com.hopcape.odo.feature.paywall.resources.pw_terms
import com.hopcape.odo.feature.paywall.resources.pw_terms_trial
import org.jetbrains.compose.resources.stringResource
import com.hopcape.odo.feature.paywall.resources.pw_badge_refuel
import com.hopcape.odo.feature.paywall.resources.pw_headline_refuel
import com.hopcape.odo.feature.paywall.resources.pw_subtitle_refuel

/**
 * The Pro paywall — one offer, framed by [PaywallUiState.trigger] (generic / scans-exhausted /
 * a fresh savings win). Feature list, monthly-vs-annual selector, the store CTA and the terms
 * Play requires above it.
 *
 * **No price is written anywhere in this file.** Every figure comes from the offer the store
 * answered with, which is why the plan cards and the CTA only exist once it has loaded. While
 * it is loading there is a spinner, and if it failed there is a retry — never a placeholder
 * price, because a figure this screen cannot confirm is one the owner might be charged
 * something else for.
 *
 * State-free: renders [state] and forwards intents.
 */
@Composable
internal fun PaywallScreen(
    state: PaywallUiState,
    onEvent: (PaywallEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val tone = badgeTone(state.trigger)

    Box(modifier.fillMaxSize().background(OdoTheme.colors.bg)) {
        // A soft, context-toned glow that bleeds edge-to-edge from the very top — behind the
        // status bar and top bar — so it fades with nothing above it (no seam at the top bar).
        Box(
            Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.13f), Color.Transparent))),
        )
        CompositionLocalProvider(LocalContentColor provides OdoTheme.colors.text) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                PaywallTopBar(state, onEvent)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = OdoTheme.spacing.screenEdge)
                        .padding(bottom = OdoTheme.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
                ) {
                    Entrance(0, visible) { ContextBadge(state, tone) }
                    Entrance(1, visible) { HeadlineBlock(state) }
                    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                        FEATURES.forEachIndexed { index, feature ->
                            Entrance(2 + index, visible) { FeatureRow(feature) }
                        }
                    }
                    Entrance(6, visible) { OfferBlock(state, onEvent) }
                    Entrance(7, visible) { Footer() }
                }
            }
        }
    }
}

// --- Top bar ---

@Composable
private fun PaywallTopBar(state: PaywallUiState, onEvent: (PaywallEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.surface)
                .clickable { onEvent(PaywallEvent.CloseTapped) },
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(
                IcClose,
                contentDescription = stringResource(Res.string.pw_cd_close),
                tint = OdoTheme.colors.text,
                size = OdoTheme.iconSizes.medium,
            )
        }
        Box(Modifier.weight(1f))
        // Every store requires this: the same account on a new phone has already paid, and
        // must be able to say so without paying again.
        OdoText(
            stringResource(Res.string.pw_restore),
            style = OdoTheme.typography.label,
            color = if (state.restoring) OdoTheme.colors.textMuted else OdoTheme.colors.textDim,
            modifier = Modifier
                .clip(OdoTheme.shapes.pill)
                .clickable(enabled = !state.busy) { onEvent(PaywallEvent.RestoreTapped) }
                .padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
        )
    }
}

// --- Header ---

@Composable
private fun ContextBadge(state: PaywallUiState, tone: Color) {
    Row(
        modifier = Modifier
            .clip(OdoTheme.shapes.pill)
            .background(tone.copy(alpha = 0.14f))
            .border(1.dp, tone.copy(alpha = 0.5f), OdoTheme.shapes.pill)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(IcStarFilled, contentDescription = null, tint = tone, size = OdoTheme.iconSizes.small)
        OdoText(badgeText(state), style = OdoTheme.typography.caption, color = tone)
    }
}

@Composable
private fun HeadlineBlock(state: PaywallUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoText(
            headlineText(state),
            style = OdoTheme.typography.display.copy(fontSize = 34.sp, lineHeight = 40.sp),
            color = OdoTheme.colors.text,
        )
        OdoText(subtitleText(state), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
    }
}

// --- Feature list ---

private data class Feature(val icon: ImageVector, val key: String)

/** The four things Pro does. Each one works today — nothing here is a promise. */
private val FEATURES = listOf(
    Feature(IcCamera, "scans"),
    Feature(IcShieldCheck, "documents"),
    Feature(IcSpeedometer, "health"),
    Feature(IcTagFilled, "export"),
)

@Composable
private fun FeatureRow(feature: Feature) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(feature.icon, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(featureTitle(feature.key), style = OdoTheme.typography.heading)
            OdoText(featureSubtitle(feature.key), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}

// --- The offer: plans, terms, CTA ---

/**
 * Everything that depends on the store's answer, in one place.
 *
 * The three states are the whole point of reading prices at run time. Loading is a spinner
 * rather than skeleton cards with fake numbers in them; failure is a sentence and a retry;
 * only [Loadable.Ready] draws a price or a button that can take money.
 */
@Composable
private fun OfferBlock(state: PaywallUiState, onEvent: (PaywallEvent) -> Unit) {
    when (val offer = state.offer) {
        is Loadable.Loading -> LoadingOffer()
        is Loadable.Failed -> FailedOffer(offer, onEvent)
        is Loadable.Ready -> ReadyOffer(state, offer.value, onEvent)
    }
}

@Composable
private fun LoadingOffer() {
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = OdoTheme.colors.accent)
    }
}

@Composable
private fun FailedOffer(failed: Loadable.Failed, onEvent: (PaywallEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(
            failed.message.asString(),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
        OdoButton(
            text = stringResource(Res.string.pw_retry),
            onClick = { onEvent(PaywallEvent.RetryTapped) },
            variant = OdoButtonVariant.Secondary,
        )
    }
}

@Composable
private fun ReadyOffer(state: PaywallUiState, offer: PaywallOffer, onEvent: (PaywallEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        PlanSelector(offer, enabled = !state.busy, onSelect = { onEvent(PaywallEvent.PlanSelected(it)) })
        offer.selected?.let { selected ->
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                // Above the button, not below it: Play requires the terms before the tap, not
                // as small print after it.
                Terms(selected)
                StartProButton(selected, busy = state.busy, onStart = { onEvent(PaywallEvent.StartProTapped) })
                state.notice?.let { notice ->
                    OdoText(
                        notice.asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanSelector(offer: PaywallOffer, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        offer.plans.forEach { plan ->
            PlanCard(
                modifier = Modifier.weight(1f),
                plan = plan,
                selected = plan.id == offer.selectedPlanId,
                enabled = enabled,
                // Only the annual card carries it, and only when there is an honest number.
                badge = offer.savingPercent
                    ?.takeIf { plan.period == BillingPeriod.ANNUAL }
                    ?.let { stringResource(Res.string.pw_plan_annual_badge, it) },
                onClick = { onSelect(plan.id) },
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: PaywallPlanCard,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val accent = OdoTheme.colors.accent
    val borderColor by animateColorAsState(if (selected) accent else OdoTheme.colors.border, tween(220), label = "planBorder")
    val container by animateColorAsState(
        if (selected) accent.copy(alpha = 0.10f) else OdoTheme.colors.surface,
        tween(220),
        label = "planBg",
    )
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OdoTheme.shapes.card)
                .background(container)
                .border(if (selected) 2.dp else 1.dp, borderColor, OdoTheme.shapes.card)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OdoText(
                    planTitle(plan.period),
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.textDim,
                    modifier = Modifier.weight(1f),
                )
                PlanRadio(selected)
            }
            OdoText(plan.price, style = OdoTheme.typography.heading.copy(fontSize = 26.sp))
            OdoText(planPeriod(plan), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        if (badge != null) {
            OdoText(
                badge,
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.bg,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-10).dp)
                    .clip(OdoTheme.shapes.pill)
                    .background(OdoTheme.colors.success)
                    .padding(horizontal = OdoTheme.spacing.sm, vertical = 2.dp),
            )
        }
    }
}

/** An accent radio whose inner dot springs in when selected. */
@Composable
private fun PlanRadio(selected: Boolean) {
    val fill by animateFloatAsState(if (selected) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "radioFill")
    val accent = OdoTheme.colors.accent
    val idle = OdoTheme.colors.textMuted
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val ringColor = Color(
            red = idle.red + (accent.red - idle.red) * fill,
            green = idle.green + (accent.green - idle.green) * fill,
            blue = idle.blue + (accent.blue - idle.blue) * fill,
            alpha = 1f,
        )
        drawCircle(color = ringColor, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        drawCircle(color = accent, radius = (size.minDimension / 2 - stroke) * 0.62f * fill)
    }
}

/**
 * What the owner is agreeing to, in the plainest sentence the facts allow.
 *
 * Required by Play before the button: the price, how often it bills, that it renews, and how
 * long the trial runs. Two sentences rather than one with an optional clause, because a plan
 * with no trial must not carry copy shaped like it has one.
 */
@Composable
private fun Terms(plan: PaywallPlanCard) {
    val period = stringResource(
        if (plan.period == BillingPeriod.ANNUAL) Res.string.pw_period_annual else Res.string.pw_period_monthly,
    )
    val text = when {
        // A one-off never renews, so it must not carry the renewal sentence Play requires
        // for subscriptions — saying "renews automatically until you cancel" about a
        // lifetime purchase would be false, and about the worst place to be false.
        plan.period == BillingPeriod.LIFETIME -> stringResource(Res.string.pw_terms_lifetime, plan.price)
        plan.trialDays != null -> stringResource(Res.string.pw_terms_trial, plan.trialDays, plan.price)
        else -> stringResource(Res.string.pw_terms, plan.price, period)
    }
    OdoText(
        text,
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StartProButton(plan: PaywallPlanCard, busy: Boolean, onStart: () -> Unit) {
    // A slow "breathing" glow so the CTA reads as the primary action without shouting.
    val glow = rememberInfiniteTransition(label = "ctaGlow")
    val elevation by glow.animateFloat(
        initialValue = 10f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ctaElevation",
    )
    // The trial is the offer when there is one — leading with a price the owner will not pay
    // for a week is a worse version of the same sentence.
    val label = plan.trialDays
        ?.let { stringResource(Res.string.pw_cta_trial, it) }
        ?: stringResource(Res.string.pw_cta, plan.price)
    OdoButton(
        text = label,
        onClick = onStart,
        loading = busy,
        loadingText = stringResource(Res.string.pw_cta_working),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation.dp,
                OdoTheme.shapes.pill,
                clip = false,
                ambientColor = OdoTheme.colors.accent,
                spotColor = OdoTheme.colors.accent,
            ),
    )
}

@Composable
private fun Footer() {
    OdoText(
        stringResource(Res.string.pw_footer),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.md),
    )
}

// --- Entrance stagger ---

@Composable
private fun Entrance(index: Int, visible: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 480, delayMillis = 70 * index, easing = FastOutSlowInEasing),
        label = "entrance$index",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 28.dp.toPx()
        },
    ) { content() }
}

// --- Copy resolution ---

private fun savingsText(amountPaise: Long): String = Amount.of(amountPaise).getOrNull()?.formatRupees() ?: ""

@Composable
private fun badgeTone(trigger: PaywallTrigger): Color = when (trigger) {
    PaywallTrigger.GENERIC -> OdoTheme.colors.accent
    PaywallTrigger.SCANS_EXHAUSTED -> OdoTheme.colors.warning
    PaywallTrigger.SAVINGS -> OdoTheme.colors.success
    PaywallTrigger.SMART_REFUEL -> OdoTheme.colors.accent
}

@Composable
private fun badgeText(state: PaywallUiState): String = when (state.trigger) {
    PaywallTrigger.GENERIC -> stringResource(Res.string.pw_badge_generic)
    PaywallTrigger.SCANS_EXHAUSTED -> stringResource(Res.string.pw_badge_scans)
    PaywallTrigger.SAVINGS -> stringResource(Res.string.pw_badge_savings, savingsText(state.amountPaise))
    PaywallTrigger.SMART_REFUEL -> stringResource(Res.string.pw_badge_refuel)
}

@Composable
private fun headlineText(state: PaywallUiState): String = when (state.trigger) {
    PaywallTrigger.GENERIC -> stringResource(Res.string.pw_headline_generic)
    PaywallTrigger.SCANS_EXHAUSTED -> stringResource(Res.string.pw_headline_scans)
    PaywallTrigger.SAVINGS -> stringResource(Res.string.pw_headline_savings)
    PaywallTrigger.SMART_REFUEL -> stringResource(Res.string.pw_headline_refuel)
}

@Composable
private fun subtitleText(state: PaywallUiState): String = when (state.trigger) {
    PaywallTrigger.GENERIC -> stringResource(Res.string.pw_subtitle_generic)
    PaywallTrigger.SCANS_EXHAUSTED -> stringResource(Res.string.pw_subtitle_scans, state.freeScans)
    PaywallTrigger.SAVINGS -> stringResource(Res.string.pw_subtitle_savings, savingsText(state.amountPaise))
    PaywallTrigger.SMART_REFUEL -> stringResource(Res.string.pw_subtitle_refuel)
}

@Composable
private fun planTitle(period: BillingPeriod): String = stringResource(
    when (period) {
        BillingPeriod.MONTHLY -> Res.string.pw_plan_monthly
        BillingPeriod.ANNUAL -> Res.string.pw_plan_annual
        BillingPeriod.LIFETIME -> Res.string.pw_plan_lifetime
    },
)

@Composable
private fun planPeriod(plan: PaywallPlanCard): String = when (plan.period) {
    BillingPeriod.MONTHLY -> stringResource(Res.string.pw_plan_monthly_period)
    // The store's own per-month figure, so nothing here divides a price.
    BillingPeriod.ANNUAL -> stringResource(Res.string.pw_plan_annual_period, plan.pricePerMonth)
    // No per-month line: a one-off has no month to divide by, and inventing one would be
    // the app doing arithmetic the store never sanctioned.
    BillingPeriod.LIFETIME -> stringResource(Res.string.pw_plan_lifetime_period)
}

@Composable
private fun featureTitle(key: String): String = stringResource(
    when (key) {
        "scans" -> Res.string.pw_feature_scans_title
        "documents" -> Res.string.pw_feature_documents_title
        "health" -> Res.string.pw_feature_health_title
        else -> Res.string.pw_feature_export_title
    },
)

@Composable
private fun featureSubtitle(key: String): String = stringResource(
    when (key) {
        "scans" -> Res.string.pw_feature_scans_sub
        "documents" -> Res.string.pw_feature_documents_sub
        "health" -> Res.string.pw_feature_health_sub
        else -> Res.string.pw_feature_export_sub
    },
)

@OdoThemePreviews
@Composable
private fun PaywallGenericPreview() = OdoPreview(padded = false) {
    PaywallScreen(samplePaywallGeneric(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun PaywallSavingsPreview() = OdoPreview(padded = false) {
    PaywallScreen(samplePaywallSavings(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun PaywallLoadingPreview() = OdoPreview(padded = false) {
    PaywallScreen(samplePaywallLoading(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun PaywallUnavailablePreview() = OdoPreview(padded = false) {
    PaywallScreen(samplePaywallUnavailable(), onEvent = {})
}
