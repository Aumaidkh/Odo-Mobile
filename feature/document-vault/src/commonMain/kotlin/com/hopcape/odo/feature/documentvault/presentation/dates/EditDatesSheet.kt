package com.hopcape.odo.feature.documentvault.presentation.dates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoDateField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.documentvault.presentation.vault.docName
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_dates_cancel
import com.hopcape.odo.feature.documentvault.resources.dv_dates_expiry
import com.hopcape.odo.feature.documentvault.resources.dv_dates_expiry_required
import com.hopcape.odo.feature.documentvault.resources.dv_dates_issued
import com.hopcape.odo.feature.documentvault.resources.dv_dates_not_set
import com.hopcape.odo.feature.documentvault.resources.dv_dates_ok
import com.hopcape.odo.feature.documentvault.resources.dv_dates_save
import com.hopcape.odo.feature.documentvault.resources.dv_dates_subtitle
import com.hopcape.odo.feature.documentvault.resources.dv_dates_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The "edit dates" sheet **body** — the issue and expiry dates of a document already in the
 * vault. Shown as a bottom-sheet destination ([com.hopcape.odo.core.navigation.OdoDestination.Documents.EditDates]);
 * the sheet chrome comes from the navigation layer.
 *
 * State-free: renders [state] and forwards intents.
 */
@Composable
internal fun EditDatesSheetContent(
    state: EditDatesUiState,
    onEvent: (EditDatesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.dv_dates_title), style = OdoTheme.typography.title)
            OdoText(
                stringResource(Res.string.dv_dates_subtitle, docName(state.type)),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }

        Labelled(stringResource(Res.string.dv_dates_issued)) {
            DateField(state.issuedOn) { onEvent(EditDatesEvent.IssuedOnChanged(it)) }
        }

        Labelled(stringResource(Res.string.dv_dates_expiry)) {
            DateField(state.expiresOn) { onEvent(EditDatesEvent.ExpiresOnChanged(it)) }
        }

        state.error?.let { message ->
            OdoText(message.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.danger)
        }
        if (state.needsExpiry && state.expiresOn == null) {
            OdoText(
                stringResource(Res.string.dv_dates_expiry_required),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }

        OdoButton(
            text = stringResource(Res.string.dv_dates_save),
            onClick = { onEvent(EditDatesEvent.SaveTapped) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DateField(date: LocalDate?, onDateChange: (LocalDate) -> Unit) {
    OdoDateField(
        date = date,
        formatted = date?.let { formatDate(it) }.orEmpty(),
        placeholder = stringResource(Res.string.dv_dates_not_set),
        confirmLabel = stringResource(Res.string.dv_dates_ok),
        cancelLabel = stringResource(Res.string.dv_dates_cancel),
        onDateChange = onDateChange,
    )
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(text = label, style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
        content()
    }
}

@OdoThemePreviews
@Composable
private fun EditDatesSheetPreview() = OdoPreview(padded = false) {
    EditDatesSheetContent(
        state = EditDatesUiState(
            type = DocumentType.INSURANCE,
            issuedOn = LocalDate(2025, 8, 4),
            expiresOn = LocalDate(2026, 8, 4),
        ),
        onEvent = {},
    )
}

@OdoThemePreviews
@Composable
private fun EditDatesSheetEmptyPreview() = OdoPreview(padded = false) {
    EditDatesSheetContent(state = EditDatesUiState(type = DocumentType.PUC), onEvent = {})
}
