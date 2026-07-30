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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBarChart
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcChatOutlined
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.icons.IcStarFilled
import com.hopcape.odo.core.designsystem.icons.IcTagFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_badge_generic
import com.hopcape.odo.feature.paywall.resources.pw_badge_savings
import com.hopcape.odo.feature.paywall.resources.pw_badge_scans
import com.hopcape.odo.feature.paywall.resources.pw_cd_close
import com.hopcape.odo.feature.paywall.resources.pw_cta_annual
import com.hopcape.odo.feature.paywall.resources.pw_cta_monthly
import com.hopcape.odo.feature.paywall.resources.pw_feature_analytics_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_analytics_title
import com.hopcape.odo.feature.paywall.resources.pw_feature_doctor_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_doctor_title
import com.hopcape.odo.feature.paywall.resources.pw_feature_health_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_health_title
import com.hopcape.odo.feature.paywall.resources.pw_feature_scans_sub
import com.hopcape.odo.feature.paywall.resources.pw_feature_scans_title
import com.hopcape.odo.feature.paywall.resources.pw_footer
import com.hopcape.odo.feature.paywall.resources.pw_headline_generic
import com.hopcape.odo.feature.paywall.resources.pw_headline_savings
import com.hopcape.odo.feature.paywall.resources.pw_headline_scans
import com.hopcape.odo.feature.paywall.resources.pw_passport_cta
import com.hopcape.odo.feature.paywall.resources.pw_passport_sub
import com.hopcape.odo.feature.paywall.resources.pw_passport_title
import com.hopcape.odo.feature.paywall.resources.pw_plan_annual
import com.hopcape.odo.feature.paywall.resources.pw_plan_annual_badge
import com.hopcape.odo.feature.paywall.resources.pw_plan_annual_period
import com.hopcape.odo.feature.paywall.resources.pw_plan_annual_price
import com.hopcape.odo.feature.paywall.resources.pw_plan_monthly
import com.hopcape.odo.feature.paywall.resources.pw_plan_monthly_period
import com.hopcape.odo.feature.paywall.resources.pw_plan_monthly_price
import com.hopcape.odo.feature.paywall.resources.pw_restore
import com.hopcape.odo.feature.paywall.resources.pw_selling
import com.hopcape.odo.feature.paywall.resources.pw_subtitle_generic
import com.hopcape.odo.feature.paywall.resources.pw_subtitle_savings
import com.hopcape.odo.feature.paywall.resources.pw_subtitle_scans
import org.jetbrains.compose.resources.stringResource

/**
 * The Pro paywall — one offer, framed by [PaywallUiState.trigger] (generic / scans-exhausted
 * / a fresh savings win). Feature list, monthly-vs-annual selector, the Razorpay CTA, and the
 * Resale Passport upsell. Entrance is staggered; the CTA breathes a soft glow; plan selection
 * and the CTA price animate.
 *
 * State-free: renders [state] and forwards intents. Real entitlement + Razorpay land later.
 */
@Composable
internal fun PaywallScreen(
    state: PaywallUiState,
    onSelectPlan: (PaywallPlan) -> Unit,
    onStartPro: () -> Unit,
    onRestore: () -> Unit,
    onBuildPassport: () -> Unit,
    onClose: () -> Unit,
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
                PaywallTopBar(onClose, onRestore)
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
                    Entrance(6, visible) { PlanSelector(state.selectedPlan, onSelectPlan) }
                    Entrance(7, visible) { StartProButton(state.selectedPlan, onStartPro) }
                    Entrance(8, visible) { SellingDivider() }
                    Entrance(8, visible) { PassportCard(onBuildPassport) }
                    Entrance(9, visible) { Footer() }
                }
            }
        }
    }
}

// --- Top bar ---

@Composable
private fun PaywallTopBar(onClose: () -> Unit, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(OdoTheme.colors.surface).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcClose, contentDescription = stringResource(Res.string.pw_cd_close), tint = OdoTheme.colors.text, size = OdoTheme.iconSizes.medium)
        }
        Box(Modifier.weight(1f))
        OdoText(
            stringResource(Res.string.pw_restore),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.clip(OdoTheme.shapes.pill).clickable(onClick = onRestore).padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
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

private data class Feature(val icon: ImageVector, val title: String, val subtitle: String)

private val FEATURES = listOf(
    Feature(IcCamera, "scans", "scans_sub"),
    Feature(IcChatOutlined, "doctor", "doctor_sub"),
    Feature(IcSpeedometer, "health", "health_sub"),
    Feature(IcBarChart, "analytics", "analytics_sub"),
)

@Composable
private fun FeatureRow(feature: Feature) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(feature.icon, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(featureTitle(feature.title), style = OdoTheme.typography.heading)
            OdoText(featureSubtitle(feature.subtitle), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}

// --- Plans ---

@Composable
private fun PlanSelector(selected: PaywallPlan, onSelect: (PaywallPlan) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        PlanCard(
            modifier = Modifier.weight(1f),
            title = stringResource(Res.string.pw_plan_monthly),
            price = stringResource(Res.string.pw_plan_monthly_price),
            period = stringResource(Res.string.pw_plan_monthly_period),
            selected = selected == PaywallPlan.MONTHLY,
            onClick = { onSelect(PaywallPlan.MONTHLY) },
        )
        PlanCard(
            modifier = Modifier.weight(1f),
            title = stringResource(Res.string.pw_plan_annual),
            price = stringResource(Res.string.pw_plan_annual_price),
            period = stringResource(Res.string.pw_plan_annual_period),
            selected = selected == PaywallPlan.ANNUAL,
            badge = stringResource(Res.string.pw_plan_annual_badge),
            onClick = { onSelect(PaywallPlan.ANNUAL) },
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val accent = OdoTheme.colors.accent
    val borderColor by animateColorAsState(if (selected) accent else OdoTheme.colors.border, tween(220), label = "planBorder")
    val container by animateColorAsState(if (selected) accent.copy(alpha = 0.10f) else OdoTheme.colors.surface, tween(220), label = "planBg")
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OdoTheme.shapes.card)
                .background(container)
                .border(if (selected) 2.dp else 1.dp, borderColor, OdoTheme.shapes.card)
                .clickable(onClick = onClick)
                .padding(OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OdoText(title, style = OdoTheme.typography.label, color = OdoTheme.colors.textDim, modifier = Modifier.weight(1f))
                PlanRadio(selected)
            }
            OdoText(price, style = OdoTheme.typography.heading.copy(fontSize = 26.sp))
            OdoText(period, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
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

// --- CTA ---

@Composable
private fun StartProButton(selected: PaywallPlan, onStartPro: () -> Unit) {
    // A slow "breathing" glow so the CTA reads as the primary action without shouting.
    val glow = rememberInfiniteTransition(label = "ctaGlow")
    val elevation by glow.animateFloat(
        initialValue = 10f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ctaElevation",
    )
    val label = if (selected == PaywallPlan.MONTHLY) {
        stringResource(Res.string.pw_cta_monthly)
    } else {
        stringResource(Res.string.pw_cta_annual)
    }
    OdoButton(
        text = label,
        onClick = onStartPro,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation.dp, OdoTheme.shapes.pill, clip = false, ambientColor = OdoTheme.colors.accent, spotColor = OdoTheme.colors.accent),
    )
}

// --- Passport upsell ---

@Composable
private fun SellingDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        Box(Modifier.weight(1f).height(1.dp).background(OdoTheme.colors.border))
        OdoText(stringResource(Res.string.pw_selling), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(OdoTheme.colors.border))
    }
}

@Composable
private fun PassportCard(onBuild: () -> Unit) {
    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcTagFilled, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.pw_passport_title), style = OdoTheme.typography.heading)
                OdoText(stringResource(Res.string.pw_passport_sub), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
            Box(
                Modifier
                    .clip(OdoTheme.shapes.pill)
                    .border(1.dp, OdoTheme.colors.accent, OdoTheme.shapes.pill)
                    .clickable(onClick = onBuild)
                    .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
            ) {
                OdoText(stringResource(Res.string.pw_passport_cta), style = OdoTheme.typography.label, color = OdoTheme.colors.accent)
            }
        }
    }
}

@Composable
private fun Footer() {
    OdoText(
        stringResource(Res.string.pw_footer),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
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

// --- Context copy resolution ---

private fun savingsText(amountPaise: Long): String = Amount.of(amountPaise).getOrNull()?.formatRupees() ?: ""

@Composable
private fun badgeTone(trigger: PaywallTrigger): Color = when (trigger) {
    PaywallTrigger.GENERIC -> OdoTheme.colors.accent
    PaywallTrigger.SCANS_EXHAUSTED -> OdoTheme.colors.warning
    PaywallTrigger.SAVINGS -> OdoTheme.colors.success
}

@Composable
private fun badgeText(state: PaywallUiState): String = when (state.trigger) {
    PaywallTrigger.GENERIC -> stringResource(Res.string.pw_badge_generic)
    PaywallTrigger.SCANS_EXHAUSTED -> stringResource(Res.string.pw_badge_scans)
    PaywallTrigger.SAVINGS -> stringResource(Res.string.pw_badge_savings, savingsText(state.amountPaise))
}

@Composable
private fun headlineText(state: PaywallUiState): String = when (state.trigger) {
    PaywallTrigger.GENERIC -> stringResource(Res.string.pw_headline_generic)
    PaywallTrigger.SCANS_EXHAUSTED -> stringResource(Res.string.pw_headline_scans)
    PaywallTrigger.SAVINGS -> stringResource(Res.string.pw_headline_savings)
}

@Composable
private fun subtitleText(state: PaywallUiState): String = when (state.trigger) {
    PaywallTrigger.GENERIC -> stringResource(Res.string.pw_subtitle_generic)
    PaywallTrigger.SCANS_EXHAUSTED -> stringResource(Res.string.pw_subtitle_scans, state.freeScans)
    PaywallTrigger.SAVINGS -> stringResource(Res.string.pw_subtitle_savings, savingsText(state.amountPaise))
}

@Composable
private fun featureTitle(key: String): String = stringResource(
    when (key) {
        "scans" -> Res.string.pw_feature_scans_title
        "doctor" -> Res.string.pw_feature_doctor_title
        "health" -> Res.string.pw_feature_health_title
        else -> Res.string.pw_feature_analytics_title
    },
)

@Composable
private fun featureSubtitle(key: String): String = stringResource(
    when (key) {
        "scans_sub" -> Res.string.pw_feature_scans_sub
        "doctor_sub" -> Res.string.pw_feature_doctor_sub
        "health_sub" -> Res.string.pw_feature_health_sub
        else -> Res.string.pw_feature_analytics_sub
    },
)

@OdoThemePreviews
@Composable
private fun PaywallGenericPreview() = OdoPreview(padded = false) {
    PaywallScreen(samplePaywallGeneric(), onSelectPlan = {}, onStartPro = {}, onRestore = {}, onBuildPassport = {}, onClose = {})
}

@OdoThemePreviews
@Composable
private fun PaywallSavingsPreview() = OdoPreview(padded = false) {
    PaywallScreen(samplePaywallSavings(), onSelectPlan = {}, onStartPro = {}, onRestore = {}, onBuildPassport = {}, onClose = {})
}
