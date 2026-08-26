package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlinx.coroutines.launch

/**
 * Tells the owner something and waits for them to read it: a title, the message, one button.
 *
 * The one-button twin of [OdoConfirmSheet], and a sheet like everything else that rises from
 * the bottom in this app. Use it when there is nothing to decide — an outcome to acknowledge,
 * a code to note down. A confirm sheet with two buttons that do the same thing asks a question
 * that does not exist.
 *
 * Rendered only while it should be on screen — the caller holds that flag, the same way every
 * other sheet in this design system is driven. The button animates the sheet away before the
 * callback runs, so the answer never makes it vanish mid-gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoInfoSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        // A swipe down or a tap on the scrim has already animated itself away by the time this
        // arrives, so it answers directly rather than sliding out first.
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OdoTheme.colors.surface,
        modifier = modifier,
    ) {
        InfoSheetContent(
            title = title,
            body = body,
            confirmLabel = confirmLabel,
            onConfirm = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
        )
    }
}

@Composable
private fun InfoSheetContent(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(title, style = OdoTheme.typography.title, color = OdoTheme.colors.text)
            OdoText(body, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }

        OdoButton(confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
    }
}

@OdoThemePreviews
@Composable
private fun OdoInfoSheetContentPreview() = OdoPreview {
    InfoSheetContent(
        title = "Diagnostics queued",
        body = "Quote this code when you reply to support:\n\nODO-AB12-CD34",
        confirmLabel = "Done",
        onConfirm = {},
    )
}
