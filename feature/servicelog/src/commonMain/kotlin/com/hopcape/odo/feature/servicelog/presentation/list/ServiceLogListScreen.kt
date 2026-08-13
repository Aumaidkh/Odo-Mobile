package com.hopcape.odo.feature.servicelog.presentation.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.icons.IcJournalPlus
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.servicelog.presentation.list.components.CombinedHeader
import com.hopcape.odo.feature.servicelog.presentation.list.model.ServiceLogDirection
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_action_enter_manually
import com.hopcape.odo.feature.servicelog.resources.sl_action_scan
import com.hopcape.odo.feature.servicelog.resources.sl_cd_add
import com.hopcape.odo.feature.servicelog.resources.sl_cd_share
import com.hopcape.odo.feature.servicelog.resources.sl_direction_ledger
import com.hopcape.odo.feature.servicelog.resources.sl_direction_timeline
import com.hopcape.odo.feature.servicelog.resources.sl_empty_action_log_first
import com.hopcape.odo.feature.servicelog.resources.sl_empty_body
import com.hopcape.odo.feature.servicelog.resources.sl_empty_body_record
import com.hopcape.odo.feature.servicelog.resources.sl_empty_title
import com.hopcape.odo.feature.servicelog.resources.sl_empty_title_record
import com.hopcape.odo.feature.servicelog.resources.sl_list_title
import org.jetbrains.compose.resources.stringResource

/**
 * The service-log list — the feature's home. Stateless: it renders [state] and reports
 * what the owner did through [onEvent]. A shared header (spend/savings + record score)
 * sits above a segmented direction toggle; only the list below the toggle swaps between
 * Ledger (1a) and Timeline (1b), sliding toward the tapped segment.
 */
@Composable
internal fun ServiceLogListScreen(
    state: ServiceLogListUiState,
    onEvent: (ServiceLogListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val direction = state.direction
    OdoScreen(
        title = stringResource(Res.string.sl_list_title),
        onBack = { onEvent(ServiceLogListEvent.Open.Back) },
        modifier = modifier,
        actions = { TopBarAction(direction, onEvent) },
        floatingActionButton = {
            // Only over a loaded list: the empty state carries its own entry points
            // ("Scan bill" / "Enter manually"), so a FAB there duplicates them.
            if (state.content is ServiceLogListUiState.Content.Loaded) {
                AddServiceFab(onClick = { onEvent(ServiceLogListEvent.Open.AddForm) })
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            when (val content = state.content) {
                ServiceLogListUiState.Content.Loading ->
                    Box(Modifier.weight(1f).fillMaxSize()) { LoadingBox() }

                is ServiceLogListUiState.Content.Failed ->
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        OdoText(
                            text = content.message.asString(),
                            style = OdoTheme.typography.body,
                            color = OdoTheme.colors.textDim,
                        )
                    }

                ServiceLogListUiState.Content.Empty -> {
                    DirectionSegmentedToggle(direction, onEvent)
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        ServiceLogEmpty(direction, onEvent)
                    }
                }

                is ServiceLogListUiState.Content.Loaded -> {
                    CombinedHeader(content)
                    DirectionSegmentedToggle(direction, onEvent)
                    val motion = OdoTheme.motion
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        AnimatedContent(
                            targetState = direction,
                            transitionSpec = {
                                // Slide toward the tapped segment (Timeline is on the right).
                                val forward = targetState.ordinal > initialState.ordinal
                                val fade = tween<Float>(motion.baseMillis, easing = motion.easeStandard)
                                val slide = tween<IntOffset>(motion.baseMillis, easing = motion.easeStandard)
                                val enter = fadeIn(fade) + slideInHorizontally(slide) { w -> if (forward) w / 8 else -w / 8 }
                                val exit = fadeOut(fade) + slideOutHorizontally(slide) { w -> if (forward) -w / 8 else w / 8 }
                                enter togetherWith exit
                            },
                            label = "servicelog-direction",
                        ) { dir ->
                            when (dir) {
                                ServiceLogDirection.LEDGER -> LedgerList(content, state.filter, onEvent)
                                ServiceLogDirection.TIMELINE -> TimelineList(content, onEvent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Top-bar action — Timeline's share only; the Ledger carries no top-bar action. */
@Composable
private fun TopBarAction(
    direction: ServiceLogDirection,
    onEvent: (ServiceLogListEvent) -> Unit,
) {
    val fast = OdoTheme.motion.fastMillis
    AnimatedContent(
        targetState = direction,
        transitionSpec = {
            val spec = tween<Float>(fast)
            fadeIn(spec) togetherWith fadeOut(spec)
        },
        label = "servicelog-action",
    ) { dir ->
        when (dir) {
            ServiceLogDirection.LEDGER -> {}
            ServiceLogDirection.TIMELINE -> OdoCircularIconButton(
                imageVector = IcShare,
                contentDescription = stringResource(Res.string.sl_cd_share),
                onClick = { onEvent(ServiceLogListEvent.Open.ShareRecord) },
            )
        }
    }
}

/** The Ledger / Timeline segmented control; the selected segment's fill animates. */
@Composable
private fun DirectionSegmentedToggle(
    direction: ServiceLogDirection,
    onEvent: (ServiceLogListEvent) -> Unit,
) {
    val onChange: (ServiceLogDirection) -> Unit = { onEvent(ServiceLogListEvent.View.DirectionSelected(it)) }
    Surface(shape = OdoTheme.shapes.pill, color = OdoTheme.colors.surfaceRaised) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(OdoTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            Segment(
                text = stringResource(Res.string.sl_direction_ledger),
                selected = direction == ServiceLogDirection.LEDGER,
                onClick = { onChange(ServiceLogDirection.LEDGER) },
                modifier = Modifier.weight(1f),
            )
            Segment(
                text = stringResource(Res.string.sl_direction_timeline),
                selected = direction == ServiceLogDirection.TIMELINE,
                onClick = { onChange(ServiceLogDirection.TIMELINE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spec = tween<Color>(OdoTheme.motion.fastMillis, easing = OdoTheme.motion.easeStandard)
    val bg by animateColorAsState(if (selected) OdoTheme.colors.accent else Color.Transparent, spec, label = "seg-bg")
    val fg by animateColorAsState(if (selected) OdoTheme.colors.onAccent else OdoTheme.colors.textDim, spec, label = "seg-fg")
    Box(
        modifier = modifier
            .clip(OdoTheme.shapes.pill)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = OdoTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        OdoText(text = text, style = OdoTheme.typography.label, color = fg)
    }
}

/** The orange "log a service" FAB, which scales + fades in the first time the list appears. */
@Composable
private fun AddServiceFab(onClick: () -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = fadeIn(tween(OdoTheme.motion.baseMillis)) +
            scaleIn(tween(OdoTheme.motion.baseMillis, easing = OdoTheme.motion.easeStandard), initialScale = 0.6f),
        exit = fadeOut() + scaleOut(),
    ) {
        Surface(
            onClick = onClick,
            shape = OdoTheme.shapes.card,
            color = OdoTheme.colors.accent,
            contentColor = OdoTheme.colors.onAccent,
            shadowElevation = 6.dp,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                OdoIcon(IcPlusLarge, contentDescription = stringResource(Res.string.sl_cd_add))
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OdoLoadingIndicator()
    }
}

/** Empty state — direction-specific copy + entry points (mockup EMPTY STATE). */
@Composable
private fun ServiceLogEmpty(
    direction: ServiceLogDirection,
    onEvent: (ServiceLogListEvent) -> Unit,
) {
    val onScanBill = { onEvent(ServiceLogListEvent.Open.BillScanner) }
    val onAddLog = { onEvent(ServiceLogListEvent.Open.AddForm) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (direction) {
            ServiceLogDirection.LEDGER -> OdoEmptyState(
                title = stringResource(Res.string.sl_empty_title),
                message = stringResource(Res.string.sl_empty_body),
                icon = {
                    Surface(
                        shadowElevation = OdoTheme.elevation.level1,
                        shape = OdoTheme.shapes.small
                    ) {
                        OdoIcon(
                            IcJournal,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(OdoTheme.spacing.xxl)
                            ,
                        )
                    }
                },
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                        OdoButton(text = stringResource(Res.string.sl_action_scan), onClick = onScanBill)
                        OdoButton(
                            text = stringResource(Res.string.sl_action_enter_manually),
                            onClick = onAddLog,
                            variant = OdoButtonVariant.Tertiary,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .offset(x = (-12).dp),
                        )
                    }
                },
            )
            ServiceLogDirection.TIMELINE -> OdoEmptyState(
                title = stringResource(Res.string.sl_empty_title_record),
                message = stringResource(Res.string.sl_empty_body_record),
                icon = {
                    Surface(
                        shadowElevation = OdoTheme.elevation.level1,
                        shape = OdoTheme.shapes.small
                    ) {
                        OdoIcon(
                            IcJournalPlus,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(OdoTheme.spacing.xxl)
                            ,
                        )
                    }
                },
                action = {
                    OdoButton(text = stringResource(Res.string.sl_empty_action_log_first), onClick = onAddLog)
                },
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun ServiceLogEmptyLedgerPreview() = OdoPreview(padded = false) {
    ServiceLogEmpty(ServiceLogDirection.LEDGER, onEvent = {})
}

@OdoThemePreviews
@Composable
private fun ServiceLogEmptyTimelinePreview() = OdoPreview(padded = false) {
    ServiceLogEmpty(ServiceLogDirection.TIMELINE, onEvent = {})
}
