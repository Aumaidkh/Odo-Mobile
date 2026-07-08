package com.hopcape.odo.feature.servicelog.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The service-log list — the feature's home. Stateless by design: the route host
 * owns navigation and (from Step 3) the ViewModel state.
 *
 * Placeholder for now — Step 2 only proves [com.hopcape.odo.core.navigation.OdoDestination.ServiceLog.List]
 * is registered and routes correctly. Step 3 replaces the body with the real
 * Ledger (1a) / Timeline (1b) UI driven by list UI-state.
 */
@Composable
internal fun ServiceLogListScreen(
    state: ServiceLogListUiState,
    carId: String,
    onOpenDetail: (logId: String) -> Unit,
    onAddLog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(title = "Service Log", onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
        ) {
            // Placeholder — Step 3 renders the Ledger (1a) / Timeline (1b) list from `state`.
            OdoText(
                text = "Service Log · list\n(car $carId)\n" +
                    "phase: ${state.content::class.simpleName} · filter: ${state.filter}",
                style = OdoTheme.typography.body,
                textAlign = TextAlign.Center,
            )
            OdoButton(text = "Open a service", onClick = { onOpenDetail("sample-log") })
            OdoButton(text = "Log a service", onClick = onAddLog, variant = OdoButtonVariant.Secondary)
        }
    }
}
