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
 * Asks before something happens, from the bottom of the screen: a title, what the answer will
 * actually do, and two buttons.
 *
 * The sheet twin of [OdoConfirmDialog], and the one to reach for when the question is a step in
 * something the owner started rather than an interruption of it. A dialog lands in the middle of
 * the screen and stops everything; a sheet rises from the thumb, keeps the screen it came from
 * visible behind it, and is dismissed by the gesture the owner already uses everywhere else in
 * this app. Destructive, out-of-nowhere confirmations still belong in [OdoConfirmDialog].
 *
 * Rendered only while it should be on screen — the caller holds that flag, the same way every
 * other sheet in this design system is driven. Both buttons animate the sheet away before the
 * callback runs, so the answer never makes it vanish mid-gesture.
 *
 * @param destructive whether the confirm action is the dangerous one. Defaults to false, the
 *   opposite of [OdoConfirmDialog]'s default: a question worth interrupting for is usually
 *   destructive, and a question worth a sheet usually isn't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Slide out first, then tell the caller — otherwise the state change that closes this sheet
    // removes it from composition before the animation has anywhere to play.
    fun settle(answer: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { answer() }
    }

    ModalBottomSheet(
        // A swipe down or a tap on the scrim has already animated itself away by the time this
        // arrives, so it answers directly rather than going through settle().
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OdoTheme.colors.surface,
        modifier = modifier,
    ) {
        ConfirmSheetContent(
            title = title,
            body = body,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            destructive = destructive,
            onConfirm = { settle(onConfirm) },
            onCancel = { settle(onDismiss) },
        )
    }
}

@Composable
private fun ConfirmSheetContent(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
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

        // No close X, unlike the picker sheets: those have nothing but a list, so the X is their
        // only way out. Here "cancel" is already a full-width button, and two ways to say no in
        // one sheet is one too many.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            OdoButton(
                confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                variant = if (destructive) OdoButtonVariant.Danger else OdoButtonVariant.Primary,
            )
            OdoButton(
                cancelLabel,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                variant = OdoButtonVariant.Tertiary,
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoConfirmSheetContentPreview() = OdoPreview {
    ConfirmSheetContent(
        title = "Turn on Bluetooth?",
        body = "Your phone will ask you to confirm. Odo uses Bluetooth only to notice when your " +
            "car's stereo connects, which is what starts a trip.",
        confirmLabel = "Turn it on",
        cancelLabel = "Not now",
        destructive = false,
        onConfirm = {},
        onCancel = {},
    )
}

@OdoThemePreviews
@Composable
private fun OdoConfirmSheetContentDestructivePreview() = OdoPreview {
    ConfirmSheetContent(
        title = "Delete every trip?",
        body = "The distances Odo worked out from your drives are removed from this phone.",
        confirmLabel = "Delete them",
        cancelLabel = "Keep them",
        destructive = true,
        onConfirm = {},
        onCancel = {},
    )
}
