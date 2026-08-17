package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The common screen container every Odo screen wraps — across every `:feature:*`
 * module. It centralises, in one place, everything a screen otherwise re-wires:
 *
 *  - brand background ([OdoTheme] `colors.bg`) and default content colour,
 *  - system-bar / display-cutout insets: the content via the underlying Material
 *    [Scaffold], plus status-bar padding on the [topBar] slot and navigation-bar
 *    padding on the [bottomBar] slot (Scaffold leaves those slots to the caller),
 *  - the optional [OdoTopBar] header (just pass a `title` + optional `onBack`),
 *  - bottom bar / FAB / snackbar slots,
 *  - the default horizontal screen-edge padding (`spacing.screenEdge`).
 *
 * Change the app-wide screen chrome here once, not in N feature screens.
 *
 * The bars/insets are applied for you; the [content] receives a [PaddingValues]
 * holding the **horizontal screen padding** so it stays flexible about how to
 * apply it:
 *
 * ```
 * // Static column:
 * OdoScreen(title = "Apni gaadi", onBack = vm::back) { padding ->
 *     Column(Modifier.fillMaxSize().padding(padding)) { /* … */ }
 * }
 *
 * // Edge-to-edge scrolling list (padding becomes the list's contentPadding):
 * OdoScreen(title = "Service log") { padding ->
 *     LazyColumn(contentPadding = padding) { /* … */ }
 * }
 * ```
 *
 * @param title when non-null, the default [OdoTopBar] is rendered with it.
 * @param onBack back affordance shown in the default top bar.
 * @param actions trailing actions for the default top bar.
 * @param topBar override the entire header (ignores [title]/[onBack]/[actions]).
 * @param snackbarHostState when non-null, a [SnackbarHost] is wired up.
 * @param contentPadding padding handed to [content]; defaults to the horizontal
 *   screen edge. Set to [PaddingValues] of 0 for a full-bleed screen.
 */
@Composable
fun OdoScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    topBar: @Composable () -> Unit = {
        if (title != null) {
            OdoTopBar(
                title = title,
                onBack = onBack,
                backContentDescription = backContentDescription,
                actions = actions,
            )
        }
    },
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    snackbarHostState: SnackbarHostState? = null,
    containerColor: Color = OdoTheme.colors.bg,
    contentColor: Color = OdoTheme.colors.text,
    contentPadding: PaddingValues = PaddingValues(horizontal = OdoTheme.spacing.screenEdge),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        // Material's Scaffold only insets the *content* slot; the top and bottom bar
        // slots draw right up to the screen edges. A Material TopAppBar pads itself, but
        // the custom Rows/Columns feature screens pass here (a close button, a CTA
        // column) do not — so with 3-button navigation the CTA sat under the system nav
        // bar and a close button under the status bar. Pad both slots here, once. The
        // inset modifiers consume what they apply, so a screen that already pads its own
        // bar (or a TopAppBar) is not padded twice, and a screen hosted under the app
        // shell — which has consumed the nav inset already — gets nothing extra.
        topBar = { Box(Modifier.statusBarsPadding()) { topBar() } },
        bottomBar = { Box(Modifier.navigationBarsPadding()) { bottomBar() } },
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        containerColor = containerColor,
        contentColor = contentColor,
    ) { insets ->
        // Bars + system insets are applied here, once, so screens never draw
        // under the status bar / top bar. The horizontal screen padding is passed
        // through for the content to apply where it fits its layout.
        Box(Modifier.fillMaxSize().padding(insets)) {
            content(contentPadding)
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoScreenPreview() = OdoPreview(padded = false) {
    OdoScreen(title = "Service log", onBack = {}) { padding ->
        Text(
            text = "Har entry me odometer reading zaroori hai.",
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.fillMaxWidth().padding(padding),
        )
    }
}
