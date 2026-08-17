package com.hopcape.odo.feature.dashboard.presentation.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.CoachMarkAnchorState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.coachMarkAnchor
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.icons.IcGarage
import com.hopcape.odo.core.designsystem.icons.IcHouse
import com.hopcape.odo.core.designsystem.icons.IcWindow
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.dashboard.resources.Res
import com.hopcape.odo.feature.dashboard.resources.db_scan
import org.jetbrains.compose.resources.stringResource

private val BarHeight = 64.dp
private val ScanSize = 60.dp
private val ScanCorner = 20.dp

/** How far the central Scan tile rises above the bar's top edge. */
private val ScanLift = 22.dp

/**
 * The dashboard's bottom navigation bar — four tabs split symmetrically around a
 * central, elevated Scan action (the app's primary acquisition hook, a FAB rather
 * than a selectable tab). Tab labels come from [OdoDestination.TopLevel.label]; the
 * icon per tab is mapped here since icons are a UI concern.
 *
 * Pure UI: it renders [selected] and emits [onSelectTab] / [onScan]; the host owns
 * the back stack. Rendered by [OdoAppScaffold] only while a top-level tab is on screen.
 */
@Composable
internal fun OdoBottomBar(
    tabs: List<OdoDestination.TopLevel>,
    selected: OdoDestination.TopLevel?,
    onSelectTab: (OdoDestination.TopLevel) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    scanAnchor: CoachMarkAnchorState? = null,
) {
    val colors = OdoTheme.colors
    // Split the tabs into the left and right clusters flanking the Scan tile.
    val half = tabs.size / 2
    val leftTabs = tabs.take(half)
    val rightTabs = tabs.drop(half)

    Box(modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surface)
                .height(BarHeight)
                .padding(horizontal = OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leftTabs.forEach { tab ->
                BottomBarTab(tab, tab == selected, { onSelectTab(tab) }, Modifier.weight(1f))
            }
            // Reserve the centre slot for the elevated Scan tile.
            Spacer(Modifier.width(ScanSize + OdoTheme.spacing.lg))
            rightTabs.forEach { tab ->
                BottomBarTab(tab, tab == selected, { onSelectTab(tab) }, Modifier.weight(1f))
            }
        }

        // The Scan tile straddles the bar's top edge, lifted above it.
        ScanTile(
            onClick = onScan,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -ScanLift)
                .let { if (scanAnchor != null) it.coachMarkAnchor(scanAnchor) else it },
        )
    }
}

@Composable
private fun RowScope.BottomBarTab(
    tab: OdoDestination.TopLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OdoTheme.colors
    val tint = if (selected) colors.accent else colors.textMuted
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick, role = Role.Tab)
            .padding(vertical = OdoTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OdoIcon(iconForTab(tab), contentDescription = tab.label, tint = tint, size = 24.dp)
        Spacer(Modifier.height(OdoTheme.spacing.xs))
        OdoText(
            text = tab.label,
            style = OdoTheme.typography.label.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScanTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OdoTheme.colors
    val label = stringResource(Res.string.db_scan)
    Column(
        modifier
            .size(ScanSize)
            // A soft accent-tinted glow so the tile reads as elevated above the bar.
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(ScanCorner),
                ambientColor = colors.accent,
                spotColor = colors.accent,
            )
            .clip(RoundedCornerShape(ScanCorner))
            .background(colors.accent)
            .clickable(onClick = onClick, role = Role.Button),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OdoIcon(IcCamera, contentDescription = label, tint = colors.onAccent, size = 22.dp)
        Spacer(Modifier.height(2.dp))
        OdoText(
            text = label.uppercase(),
            style = OdoTheme.typography.caption.copy(fontSize = 10.sp),
            color = colors.onAccent,
            maxLines = 1,
        )
    }
}

/** Maps a bottom-nav root to its bar icon. Non-tab [OdoDestination.TopLevel]s fall back. */
private fun iconForTab(tab: OdoDestination.TopLevel): ImageVector = when (tab) {
    OdoDestination.Home -> IcHouse
    OdoDestination.Timeline.List -> IcClock
    OdoDestination.CostTracker.Home -> IcCurrencyDollar
    OdoDestination.Garage.Home -> IcGarage
    else -> IcWindow
}
