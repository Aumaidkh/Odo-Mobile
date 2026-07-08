package com.hopcape.odo.feature.servicelog.presentation.form

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
 * The add / edit service form — one screen for both modes ([editLogId] non-null =
 * edit). Stateless by design: the route host owns navigation and (from Step 3) the
 * ViewModel that validates the mandatory odometer reading.
 *
 * Placeholder for now — Step 2 only proves [com.hopcape.odo.core.navigation.OdoDestination.ServiceLog.AddEdit]
 * is registered and routes correctly. Step 3 replaces the body with the real form.
 */
@Composable
internal fun ServiceLogFormScreen(
    state: ServiceLogFormUiState,
    carId: String,
    editLogId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.isEditing) "Edit service" else "Log a service"
    OdoScreen(title = title, onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
        ) {
            // Placeholder — Step 3 renders the scan/manual form from `state`.
            OdoText(
                text = "$title\n(car $carId${editLogId?.let { " · editing $it" } ?: ""})\n" +
                    "canSave: ${state.canSave}",
                style = OdoTheme.typography.body,
                textAlign = TextAlign.Center,
            )
            OdoButton(text = "Save to log", onClick = onSaved)
        }
    }
}
