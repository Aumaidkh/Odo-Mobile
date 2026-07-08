package com.hopcape.odo.feature.servicelog.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A single service entry's detail — the fairness breakdown (1a) / resale proof (1b).
 * Stateless by design: the route host owns navigation and (from Step 3) the state.
 *
 * Placeholder for now — Step 2 only proves [com.hopcape.odo.core.navigation.OdoDestination.ServiceLog.Detail]
 * is registered and routes correctly. Step 3 replaces the body with the real UI.
 */
@Composable
internal fun ServiceLogDetailScreen(
    state: ServiceLogDetailUiState,
    logId: String,
    carId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(title = "Service details", onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
        ) {
            // Placeholder — Step 3 renders the fairness (1a) / resale-proof (1b) detail from `state`.
            OdoText(
                text = "Service details\n(log $logId · car $carId)\n" +
                    "phase: ${state.content::class.simpleName}",
                style = OdoTheme.typography.body,
                textAlign = TextAlign.Center,
            )
            OdoButton(text = "Edit this service", onClick = onEdit)
        }
    }
}
