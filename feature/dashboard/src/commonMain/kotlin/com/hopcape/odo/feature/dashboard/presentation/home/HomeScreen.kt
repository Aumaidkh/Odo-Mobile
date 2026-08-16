package com.hopcape.odo.feature.dashboard.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoHealthDial
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBellFilled
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcFuelPump
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.icons.IcLightbulbFilled
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.icons.IcTagFilled
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.formatRupeesDecimal
import com.hopcape.odo.feature.dashboard.presentation.state.Loadable
import com.hopcape.odo.feature.dashboard.resources.Res
import com.hopcape.odo.feature.dashboard.resources.db_score_none
import com.hopcape.odo.feature.dashboard.resources.hm_auto_detect_title
import com.hopcape.odo.feature.dashboard.resources.hm_auto_detect_body
import com.hopcape.odo.feature.dashboard.resources.hm_add_car
import com.hopcape.odo.feature.dashboard.resources.hm_avatar_fallback
import com.hopcape.odo.feature.dashboard.resources.hm_car_line
import com.hopcape.odo.feature.dashboard.resources.hm_log_fill
import com.hopcape.odo.feature.dashboard.resources.hm_cd_bell
import com.hopcape.odo.feature.dashboard.resources.hm_cd_profile
import com.hopcape.odo.feature.dashboard.resources.hm_get_set_up
import com.hopcape.odo.feature.dashboard.resources.hm_greeting
import com.hopcape.odo.feature.dashboard.resources.hm_greeting_generic
import com.hopcape.odo.feature.dashboard.resources.hm_health_score
import com.hopcape.odo.feature.dashboard.resources.hm_insight_eyebrow
import com.hopcape.odo.feature.dashboard.resources.hm_insight_resale_eyebrow
import com.hopcape.odo.feature.dashboard.resources.hm_no_car
import com.hopcape.odo.feature.dashboard.resources.hm_no_car_body
import com.hopcape.odo.feature.dashboard.resources.hm_overcharge_caught
import com.hopcape.odo.feature.dashboard.resources.hm_per_unit
import com.hopcape.odo.feature.dashboard.resources.hm_recent
import com.hopcape.odo.feature.dashboard.resources.hm_running_cost
import com.hopcape.odo.feature.dashboard.resources.hm_scan_first
import com.hopcape.odo.feature.dashboard.resources.hm_score_waiting
import com.hopcape.odo.feature.dashboard.resources.hm_score_waiting_body
import com.hopcape.odo.feature.dashboard.resources.hm_see_breakdown
import com.hopcape.odo.feature.dashboard.resources.hm_setup_bill
import com.hopcape.odo.feature.dashboard.resources.hm_setup_bill_sub
import com.hopcape.odo.feature.dashboard.resources.hm_setup_car
import com.hopcape.odo.feature.dashboard.resources.hm_setup_docs
import com.hopcape.odo.feature.dashboard.resources.hm_setup_docs_sub
import com.hopcape.odo.feature.dashboard.resources.hm_timeline
import com.hopcape.odo.feature.dashboard.ui.attentionSubtitle
import com.hopcape.odo.feature.dashboard.ui.attentionTitle
import com.hopcape.odo.feature.dashboard.ui.bandText
import com.hopcape.odo.feature.dashboard.ui.costTrendText
import com.hopcape.odo.feature.dashboard.ui.healthNoteText
import com.hopcape.odo.feature.dashboard.ui.insightText
import com.hopcape.odo.feature.dashboard.ui.overchargeSubText
import com.hopcape.odo.feature.dashboard.ui.recentMeta
import com.hopcape.odo.feature.dashboard.ui.recentTitle
import org.jetbrains.compose.resources.stringResource
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.feature.dashboard.resources.hm_badge_pro
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_kwh
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_kilogram
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_litre
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_log
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_no_fill_body
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_no_fill
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_usual
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_last
import com.hopcape.odo.feature.dashboard.resources.hm_fuel_since_fill
import com.hopcape.odo.feature.dashboard.domain.model.TankStatus
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.designsystem.component.OdoProgressBar

/**
 * The Home tab — the dashboard's cross-feature glance: health score, cost, overcharges
 * caught, what needs attention, an insight, and the newest thing that happened (or, for a
 * new owner, the setup checklist).
 *
 * State-free: it renders [state] and reports taps as [HomeEvent]s. Every string is resolved
 * here from the typed values the state carries, so the same card can be previewed in every
 * condition without a ViewModel.
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = (state.content as? Loadable.Ready)?.value
    OdoScreen(
        modifier = modifier.testTag(HomeTestTags.SCREEN),
        bottomBar = {
            if (content != null && content.isNewUser && !content.hasNoCar) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = OdoTheme.spacing.screenEdge)
                        .padding(vertical = OdoTheme.spacing.md),
                ) {
                    OdoButton(
                        stringResource(Res.string.hm_scan_first),
                        onClick = { onEvent(HomeEvent.ScanBillTapped) },
                        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.SCAN_FIRST_BUTTON),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            when (state.content) {
                is Loadable.Loading -> HomeSkeleton()
                is Loadable.Failed -> HomeError(state.content.message.asString())
                is Loadable.Ready -> HomeBody(state.content.value, state.offerAutoDetect, state.autoDetectLocked, onEvent)
            }
        }
    }
}

@Composable
private fun HomeBody(
    content: HomeContent,
    offerAutoDetect: Boolean,
    autoDetectLocked: Boolean,
    onEvent: (HomeEvent) -> Unit,
) {
    HomeHeader(content, onEvent)
    when {
        content.hasNoCar -> NoCarContent(onEvent)
        content.isNewUser -> NewUserContent(content, onEvent)
        else -> ScoredContent(content, offerAutoDetect, autoDetectLocked, onEvent)
    }
}

// --- Header ---------------------------------------------------------------------

@Composable
private fun HomeHeader(content: HomeContent, onEvent: (HomeEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val greeting = if (content.userName.isBlank()) {
                stringResource(Res.string.hm_greeting_generic)
            } else {
                stringResource(Res.string.hm_greeting, content.userName)
            }
            OdoText(
                greeting,
                style = OdoTheme.typography.title,
                maxLines = 1,
                modifier = Modifier.testTag(HomeTestTags.GREETING),
            )
            if (content.carName.isNotBlank()) {
                OdoText(
                    carLine(content),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    maxLines = 1,
                    modifier = Modifier.testTag(HomeTestTags.CAR_LINE),
                )
            }
        }
        /*
         * The bell carries no unread dot. Nothing in the app knows what "unread" means yet
         * — reminders have no domain until M4 — and a dot that is always on, or always
         * derived from the card directly below it, is a worse lie than no dot at all.
         */
        if (!content.hasNoCar) {
            CircleButton(
                onClick = { onEvent(HomeEvent.BellTapped) },
                modifier = Modifier.testTag(HomeTestTags.BELL_BUTTON),
            ) {
                OdoIcon(
                    IcBellFilled,
                    contentDescription = stringResource(Res.string.hm_cd_bell),
                    size = OdoTheme.iconSizes.medium,
                )
            }
        }
        // The letter inside is the owner's initial, which a screen reader would announce
        // as a single character; the label is what says what tapping it does.
        val profileLabel = stringResource(Res.string.hm_cd_profile)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.accent)
                .clickable(onClick = { onEvent(HomeEvent.ProfileTapped) })
                .semantics { contentDescription = profileLabel }
                .testTag(HomeTestTags.PROFILE_BUTTON),
            contentAlignment = Alignment.Center,
        ) {
            OdoText(
                content.userName.take(1).ifBlank { stringResource(Res.string.hm_avatar_fallback) },
                style = OdoTheme.typography.heading,
                color = OdoTheme.colors.onAccent,
            )
        }
    }
}

/** "Swift VXI · 54,000 km", dropping the reading when the car has none. */
@Composable
private fun carLine(content: HomeContent): String {
    val odometer = content.odometer ?: return content.carName
    return stringResource(Res.string.hm_car_line, content.carName, LocalOdoDistanceFormat.current.format(odometer.km))
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(OdoTheme.colors.surfaceRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

// --- Scored ---------------------------------------------------------------------

@Composable
private fun ScoredContent(
    content: HomeContent,
    offerAutoDetect: Boolean,
    autoDetectLocked: Boolean,
    onEvent: (HomeEvent) -> Unit,
) {
    HealthCard(content, onEvent)
    FuelCard(content.tank, onEvent)
    if (offerAutoDetect) AutoDetectOffer(autoDetectLocked, onEvent)
    StatsRow(content)
    AttentionCard(content.attention, onEvent)
    content.insight?.let { InsightCard(it) }
    content.recent?.let { RecentSection(it, onEvent) }
}

/**
 * The only place automatic fuel logging is discoverable.
 *
 * Without it the feature lives three screens down — Profile, Notifications, Auto-detect — and
 * nobody looking for "log my fuel automatically" would think to look under notifications.
 *
 * It opens the explanation, never the permission. What the owner meets first is what would be
 * read and what would not, and the system's own prompt only after they have chosen to go on;
 * a card that asked for notification access on tap would be asking before it explained.
 *
 * Gone the moment detection is on, so it is an offer rather than an advert.
 */
@Composable
private fun AutoDetectOffer(locked: Boolean, onEvent: (HomeEvent) -> Unit) {
    OdoCard(
        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.AUTO_DETECT_OFFER),
        onClick = { onEvent(HomeEvent.AutoDetectTapped) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                IcFuelPump,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.medium,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OdoText(
                        stringResource(Res.string.hm_auto_detect_title),
                        style = OdoTheme.typography.label,
                    )
                    // Named before it is tapped. A card that opens a paywall without saying
                    // so first reads as a trick, and the owner who cannot buy today should be
                    // able to skip it without spending a tap to find out.
                    if (locked) {
                        OdoBadge(
                            text = stringResource(Res.string.hm_badge_pro),
                            tone = OdoBadgeTone.Accent,
                        )
                    }
                }
                OdoText(
                    stringResource(Res.string.hm_auto_detect_body),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
            OdoIcon(
                IcChevronRight,
                contentDescription = null,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
            )
        }
    }
}

/**
 * "Log a fill" — the shortcut the whole smart-refuel feature hangs off.
 *
 * High on the screen rather than buried in the garage, because the owner opening Odo right
 * after paying at a pump is the single most common reason this screen is looked at, and every
 * tap between here and the amount field is one that gets a fill left unlogged.
 *
 * Primary rather than secondary, and carrying the fuel-pump icon. It was an outlined button
 * sitting between two filled cards, which put the screen's most-used action at the lowest
 * emphasis on it — it read as a link under the health score rather than the thing to tap. On
 * a screen where everything else is a card, the one button that *does* something should be
 * the one thing that looks like a button.
 */
@Composable
private fun FuelCard(tank: TankStatus, onEvent: (HomeEvent) -> Unit) {
    OdoCard(
        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.LOG_FILL_BUTTON),
        onClick = { onEvent(HomeEvent.LogFillTapped) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp)
                        .clip(OdoTheme.shapes.small)
                        .background(OdoTheme.colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    OdoIcon(
                        IcFuelPump,
                        contentDescription = null,
                        tint = OdoTheme.colors.text,
                        size = OdoTheme.iconSizes.medium,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
                ) {
                    OdoText(fuelTitle(tank), style = OdoTheme.typography.heading)
                    OdoText(
                        fuelSubtitle(tank),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
                // The whole card is tappable, so this is an affordance rather than the only
                // way in — it says what the card does without the owner having to guess that
                // a card is a button.
                OdoButton(
                    text = stringResource(Res.string.hm_fuel_log),
                    onClick = { onEvent(HomeEvent.LogFillTapped) },
                )
            }

            // Both only when there is a habit to compare against. One fill is a record, not a
            // pattern, and a bar drawn from it would be a guess wearing a measurement's face.
            tank.progress?.let { progress ->
                OdoProgressBar(progress = progress, color = OdoTheme.colors.text)
            }
            tank.typicalRange?.let { range ->
                OdoText(
                    stringResource(
                        Res.string.hm_fuel_usual,
                        LocalOdoDistanceFormat.current.format(range.km),
                    ),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textMuted,
                )
            }
        }
    }
}

/**
 * "412 km since last fill", or the invitation when there is nothing to measure.
 *
 * The distance needs both a last fill *and* a reading on it. A detected fill reaches the owner
 * at the pump, where the dashboard reading is the one number out of reach, so a fill with no
 * odometer is expected — and the card falls back to naming the fill rather than printing a
 * zero that reads like the car has not moved.
 */
@Composable
private fun fuelTitle(tank: TankStatus): String = when {
    tank.sinceLastFill != null -> stringResource(
        Res.string.hm_fuel_since_fill,
        LocalOdoDistanceFormat.current.format(tank.sinceLastFill.km),
    )
    tank.hasFill -> stringResource(Res.string.hm_log_fill)
    else -> stringResource(Res.string.hm_fuel_no_fill)
}

/** "15 Aug · Rs. 3,809 · 40.1 L" — what the last fill was, as far as it is known. */
@Composable
private fun fuelSubtitle(tank: TankStatus): String {
    val filledOn = tank.lastFilledOn ?: return stringResource(Res.string.hm_fuel_no_fill_body)
    return stringResource(
        Res.string.hm_fuel_last,
        formatDayMonth(filledOn),
        tank.lastAmount?.formatRupees().orEmpty(),
        fuelQuantity(tank),
    )
}

/**
 * "40.1 L" — the quantity in the unit the fill was sold in.
 *
 * Stored in thousandths, shown to one decimal: a pump prints two, but the second is noise
 * beside a figure the owner is reading at a glance, and rounding it here keeps the line short
 * enough to sit on one row beside the date and the amount.
 */
@Composable
private fun fuelQuantity(tank: TankStatus): String {
    val milli = tank.lastQuantityMilli ?: return ""
    val whole = milli / 1000
    val tenth = (milli % 1000) / 100
    val figure = "$whole.$tenth"
    return when (tank.lastUnit) {
        FuelUnit.KILOGRAM -> stringResource(Res.string.hm_fuel_kilogram, figure)
        FuelUnit.KILOWATT_HOUR -> stringResource(Res.string.hm_fuel_kwh, figure)
        else -> stringResource(Res.string.hm_fuel_litre, figure)
    }
}

@Composable
private fun HealthCard(content: HomeContent, onEvent: (HomeEvent) -> Unit) {
    OdoCard(modifier = Modifier.testTag(HomeTestTags.HEALTH_CARD)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoHealthDial(
                score = content.score,
                dialSize = 128.dp,
                strokeWidth = 12.dp,
                arcColor = OdoTheme.colors.accent,
                centerContent = {
                    OdoText(
                        content.score.toString(),
                        style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 40.sp),
                        modifier = Modifier.testTag(HomeTestTags.SCORE),
                    )
                    OdoText(
                        bandText(content.band),
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.accent,
                    )
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(
                    stringResource(Res.string.hm_health_score),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                )
                OdoText(healthNoteText(content.scoreDelta), style = OdoTheme.typography.body)
                LinkRow(
                    label = stringResource(Res.string.hm_see_breakdown),
                    onClick = { onEvent(HomeEvent.BreakdownTapped) },
                    modifier = Modifier.testTag(HomeTestTags.BREAKDOWN_LINK),
                )
            }
        }
    }
}

@Composable
private fun StatsRow(content: HomeContent) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        StatCard(
            label = stringResource(Res.string.hm_running_cost),
            modifier = Modifier.weight(1f).testTag(HomeTestTags.COST_CARD),
            value = {
                Row(verticalAlignment = Alignment.Bottom) {
                    val distance = LocalOdoDistanceFormat.current
                    OdoText(
                        content.perKm?.let { rate ->
                            Amount.of(distance.ratePaise(rate.paise)).getOrElse { rate }.formatRupeesDecimal()
                        } ?: stringResource(Res.string.db_score_none),
                        style = OdoTheme.typography.title,
                    )
                    if (content.perKm != null) {
                        OdoText(
                            stringResource(Res.string.hm_per_unit, distance.suffix),
                            style = OdoTheme.typography.bodySmall,
                            color = OdoTheme.colors.textDim,
                        )
                    }
                }
            },
            footer = costTrendText(content.costTrend?.percentChange, hasRate = content.perKm != null),
            // Costlier than last quarter is the only reading worth flagging; cheaper, flat
            // and "no comparison yet" are all fine news or no news.
            footerColor = if (content.costTrend?.isUp == true) {
                OdoTheme.colors.warning
            } else {
                OdoTheme.colors.textDim
            },
        )
        StatCard(
            label = stringResource(Res.string.hm_overcharge_caught),
            modifier = Modifier.weight(1f).testTag(HomeTestTags.OVERCHARGE_CARD),
            value = {
                OdoText(
                    content.overchargeTotal.formatRupees(),
                    style = OdoTheme.typography.title,
                    color = if (content.overchargesCaught > 0) {
                        OdoTheme.colors.accent
                    } else {
                        OdoTheme.colors.textDim
                    },
                )
            },
            footer = overchargeSubText(content.overchargesCaught),
            footerColor = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: @Composable () -> Unit,
    footer: String,
    footerColor: Color,
    modifier: Modifier = Modifier,
) {
    OdoCard(modifier = modifier) {
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim, maxLines = 1)
        value()
        OdoText(footer, style = OdoTheme.typography.bodySmall, color = footerColor, maxLines = 1)
    }
}

/** The one thing to act on, or the all-clear when there is nothing. */
@Composable
private fun AttentionCard(attention: CarAttention?, onEvent: (HomeEvent) -> Unit) {
    val colors = OdoTheme.colors
    val tint = when {
        attention == null -> colors.success
        attention.isOverdue -> colors.warning
        else -> colors.accent
    }
    OdoCard(
        onClick = if (attention != null) ({ onEvent(HomeEvent.AttentionTapped) }) else null,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
        modifier = Modifier.testTag(HomeTestTags.ATTENTION_CARD),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeIconTile(attentionIcon(attention), tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(attentionTitle(attention), style = OdoTheme.typography.heading)
                OdoText(
                    attentionSubtitle(attention),
                    style = OdoTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
            if (attention != null) Chevron()
        }
    }
}

private fun attentionIcon(attention: CarAttention?): ImageVector = when (attention) {
    null -> IcCheck
    is CarAttention.DocumentLapsed -> IcWarning
    is CarAttention.DocumentExpiring -> IcShieldFilled
    is CarAttention.ServiceOverdue -> IcWarning
    is CarAttention.ServiceDue -> IcSpeedometer
}

/**
 * The insight card. Never tappable: it states a fact about the record rather than asking
 * for an action, and the action it might imply is already the attention card's or the
 * checklist's.
 */
@Composable
private fun InsightCard(insight: CarInsight) {
    val eyebrow = if (insight is CarInsight.ResaleReady) {
        Res.string.hm_insight_resale_eyebrow
    } else {
        Res.string.hm_insight_eyebrow
    }
    val icon = if (insight is CarInsight.ResaleReady) IcTagFilled else IcLightbulbFilled
    OdoCard(
        border = BorderStroke(1.dp, OdoTheme.colors.border),
        modifier = Modifier.testTag(HomeTestTags.INSIGHT_CARD),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeIconTile(icon, OdoTheme.colors.textDim)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(
                    stringResource(eyebrow),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.accent,
                )
                OdoText(insightText(insight), style = OdoTheme.typography.body)
            }
        }
    }
}

@Composable
private fun RecentSection(event: ActivityEvent, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OdoText(
                stringResource(Res.string.hm_recent),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textDim,
                modifier = Modifier.weight(1f),
            )
            LinkRow(
                label = stringResource(Res.string.hm_timeline),
                onClick = { onEvent(HomeEvent.TimelineTapped) },
                modifier = Modifier.testTag(HomeTestTags.TIMELINE_LINK),
            )
        }
        val isService = event is ActivityEvent.Service
        OdoCard(
            onClick = if (isService) ({ onEvent(HomeEvent.RecentTapped) }) else null,
            modifier = Modifier.testTag(HomeTestTags.RECENT_ROW),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeIconTile(recentIcon(event), OdoTheme.colors.textDim)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OdoText(recentTitle(event), style = OdoTheme.typography.heading, maxLines = 1)
                    OdoText(
                        recentMeta(event),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                        maxLines = 1,
                    )
                }
                if (event is ActivityEvent.Service) {
                    OdoText(event.amount.formatRupees(), style = OdoTheme.typography.heading)
                }
            }
        }
    }
}

private fun recentIcon(event: ActivityEvent): ImageVector = when (event) {
    is ActivityEvent.Service -> IcJournal
    is ActivityEvent.DocumentFiled -> IcFileFilled
    is ActivityEvent.ScoreChanged -> IcTagFilled
    is ActivityEvent.FuelFilled -> IcFuelPump
    is ActivityEvent.CarAdded -> IcCar
}

// --- New user -------------------------------------------------------------------

@Composable
private fun NewUserContent(content: HomeContent, onEvent: (HomeEvent) -> Unit) {
    OdoCard(modifier = Modifier.testTag(HomeTestTags.SCORE_WAITING)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoHealthDial(
                score = 0,
                dialSize = 128.dp,
                strokeWidth = 12.dp,
                arcColor = OdoTheme.colors.border,
                centerContent = {
                    OdoText(
                        stringResource(Res.string.db_score_none),
                        style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 40.sp),
                        color = OdoTheme.colors.textMuted,
                    )
                },
            )
            OdoText(
                stringResource(Res.string.hm_score_waiting),
                style = OdoTheme.typography.heading,
                textAlign = TextAlign.Center,
            )
            OdoText(
                stringResource(Res.string.hm_score_waiting_body),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
        }
    }
    OdoText(
        stringResource(Res.string.hm_get_set_up, content.setup.doneCount, content.setup.stepCount),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textDim,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        modifier = Modifier.testTag(HomeTestTags.CHECKLIST),
    ) {
        ChecklistCard(
            title = stringResource(Res.string.hm_setup_car),
            subtitle = null,
            done = content.setup.carAdded,
            onClick = null,
            modifier = Modifier.testTag(HomeTestTags.CHECKLIST_CAR),
        )
        ChecklistCard(
            title = stringResource(Res.string.hm_setup_bill),
            subtitle = stringResource(Res.string.hm_setup_bill_sub),
            done = content.setup.billScanned,
            onClick = { onEvent(HomeEvent.ScanBillTapped) },
            modifier = Modifier.testTag(HomeTestTags.CHECKLIST_BILL),
        )
        ChecklistCard(
            title = stringResource(Res.string.hm_setup_docs),
            subtitle = stringResource(Res.string.hm_setup_docs_sub),
            done = content.setup.documentsFiled,
            onClick = { onEvent(HomeEvent.AddDocumentsTapped) },
            modifier = Modifier.testTag(HomeTestTags.CHECKLIST_DOCS),
        )
    }
}

@Composable
private fun ChecklistCard(
    title: String,
    subtitle: String?,
    done: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // A done step has nothing left to do, so it stops being tappable and loses its accent.
    val actionable = onClick != null && !done
    OdoCard(
        onClick = if (actionable) onClick else null,
        border = BorderStroke(
            1.dp,
            if (actionable) OdoTheme.colors.accent.copy(alpha = 0.4f) else OdoTheme.colors.border,
        ),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckMark(done = done)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(
                    title,
                    style = OdoTheme.typography.heading.copy(
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    ),
                    color = if (done) OdoTheme.colors.textDim else OdoTheme.colors.text,
                )
                if (subtitle != null) {
                    OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                }
            }
            if (actionable) Chevron()
        }
    }
}

@Composable
private fun CheckMark(done: Boolean) {
    if (done) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(OdoTheme.colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.onAccent, size = OdoTheme.iconSizes.small)
        }
    } else {
        Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(OdoTheme.colors.surfaceRaised))
    }
}

// --- No car, loading, failure ----------------------------------------------------

/**
 * Setup never stored a car. Everything Home shows is about a car, so it asks for one
 * instead of rendering a dashboard of zeroes.
 */
@Composable
private fun NoCarContent(onEvent: (HomeEvent) -> Unit) {
    OdoCard(modifier = Modifier.testTag(HomeTestTags.NO_CAR)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            HomeIconTile(IcCar, OdoTheme.colors.accent)
            OdoText(
                stringResource(Res.string.hm_no_car),
                style = OdoTheme.typography.heading,
                textAlign = TextAlign.Center,
            )
            OdoText(
                stringResource(Res.string.hm_no_car_body),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
            OdoButton(
                stringResource(Res.string.hm_add_car),
                onClick = { onEvent(HomeEvent.AddCarTapped) },
                modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.ADD_CAR_BUTTON),
            )
        }
    }
}

/**
 * What Home shows while the record is being read.
 *
 * Cards in the shape of the real ones rather than a spinner: the tab is switched to
 * instantly, and an empty screen for even one frame reads as "there is nothing here"
 * — which is the wrong thing to tell someone about their own car.
 */
@Composable
private fun HomeSkeleton() {
    Column(
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.SKELETON),
    ) {
        SkeletonBlock(height = 160.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            SkeletonBlock(height = 96.dp, modifier = Modifier.weight(1f))
            SkeletonBlock(height = 96.dp, modifier = Modifier.weight(1f))
        }
        SkeletonBlock(height = 80.dp)
        SkeletonBlock(height = 80.dp)
    }
}

@Composable
private fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(OdoTheme.shapes.card)
            .background(OdoTheme.colors.surfaceRaised),
    )
}

@Composable
private fun HomeError(message: String) {
    OdoCard(modifier = Modifier.testTag(HomeTestTags.ERROR)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeIconTile(IcWarning, OdoTheme.colors.warning)
            OdoText(message, style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
        }
    }
}

// --- Shared bits ----------------------------------------------------------------

@Composable
private fun HomeIconTile(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(44.dp).clip(OdoTheme.shapes.field).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.medium)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(label, style = OdoTheme.typography.label, color = OdoTheme.colors.accent)
        Chevron()
    }
}

/** The "open this" affordance in the accent tint. */
@Composable
private fun Chevron() {
    OdoIcon(
        IcChevronRight,
        contentDescription = null,
        tint = OdoTheme.colors.accent,
        size = OdoTheme.iconSizes.small,
    )
}
