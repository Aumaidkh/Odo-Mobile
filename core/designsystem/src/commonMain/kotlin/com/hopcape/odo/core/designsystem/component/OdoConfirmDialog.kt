package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Asks before something irreversible happens: a title, what will actually be lost, and two
 * buttons.
 *
 * The confirm button is [OdoButtonVariant.Danger] and is never the one under the thumb by
 * default — cancelling is the safe answer, so it is the wider, calmer control below.
 *
 * @param destructive whether the confirm action is the dangerous one. Set false for a
 *   confirmation that only asks for certainty (leaving a form, say).
 */
@Composable
fun OdoConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    confirmModifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        OdoCard(modifier = modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(title, style = OdoTheme.typography.heading)
                OdoText(body, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                OdoButton(
                    confirmLabel,
                    onClick = onConfirm,
                    modifier = confirmModifier.fillMaxWidth(),
                    variant = if (destructive) OdoButtonVariant.Danger else OdoButtonVariant.Primary,
                )
                OdoButton(
                    cancelLabel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    variant = OdoButtonVariant.Tertiary,
                )
            }
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoConfirmDialogPreview() = OdoPreview {
    OdoConfirmDialog(
        title = "Delete everything on this device?",
        body = "Your car, its history and your documents are removed from this phone.",
        confirmLabel = "Delete everything",
        cancelLabel = "Cancel",
        onConfirm = {},
        onDismiss = {},
    )
}
