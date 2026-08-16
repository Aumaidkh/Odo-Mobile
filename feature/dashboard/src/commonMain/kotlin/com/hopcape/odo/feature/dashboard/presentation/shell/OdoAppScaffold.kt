package com.hopcape.odo.feature.dashboard.presentation.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.rememberCoachMarkAnchorState
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.navigation.OdoDestination

/**
 * The app shell — a [Scaffold] whose bottom bar is the dashboard's [OdoBottomBar],
 * wrapped around the navigation host's content. Owned by the dashboard feature (the
 * shell is chrome, not business logic) and driven entirely by the composition root:
 *
 * ```
 * OdoAppScaffold(
 *     currentDestination = navigator.backStack.lastOrNull(),
 *     onSelectTab = { navigationManager.navigateTo(it, popUpTo = OdoDestination.Home) },
 *     onScan = { navigationManager.navigateTo(OdoDestination.BillScanner.Capture()) },
 * ) { padding -> OdoNavHost(navigator, navigationManager, entryProviders, Modifier.padding(padding)) }
 * ```
 *
 * The bar shows only while a bottom-nav root ([OdoDestination.topLevel]) is on screen;
 * on nested screens (a detail, the scan flow, a sheet) it hides, so [content] gets the
 * full height. The [PaddingValues] handed to [content] carry the bar's height and
 * nothing else — system-bar insets stay each screen's own business, so a full-bleed
 * screen still draws under the status bar.
 */
@Composable
fun OdoAppScaffold(
    currentDestination: OdoDestination?,
    onSelectTab: (OdoDestination.TopLevel) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val tabs = OdoDestination.topLevel
    val selected = (currentDestination as? OdoDestination.TopLevel)?.takeIf { it in tabs }

    // The SCAN tile's coach-mark anchor (#228): the tile writes its bounds here, Home
    // reads them through LocalScanCoachMarkAnchor to point its coach mark at the bar.
    val scanAnchor = rememberCoachMarkAnchorState()

    Scaffold(
        modifier = modifier,
        containerColor = OdoTheme.colors.bg,
        contentColor = OdoTheme.colors.text,
        // The shell adds no system-bar padding of its own: the app is edge-to-edge, and
        // every screen already handles its own insets (OdoScreen's scaffold, or a
        // full-bleed screen like Welcome applying statusBarsPadding to its content). If
        // the shell applied them too, a full-bleed screen would be pushed below the
        // status bar and the shell's own background would show through as a dead band
        // across the top. What [content] receives here is therefore only the height of
        // the bottom bar, when there is one.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selected != null) {
                OdoBottomBar(
                    tabs = tabs,
                    selected = selected,
                    onSelectTab = onSelectTab,
                    onScan = onScan,
                    scanAnchor = scanAnchor,
                )
            }
        },
        content = { padding ->
            CompositionLocalProvider(LocalScanCoachMarkAnchor provides scanAnchor) {
                content(padding)
            }
        },
    )
}
