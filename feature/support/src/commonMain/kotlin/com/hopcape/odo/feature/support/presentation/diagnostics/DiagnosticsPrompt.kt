package com.hopcape.odo.feature.support.presentation.diagnostics

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.component.OdoConfirmSheet
import com.hopcape.odo.core.designsystem.component.OdoInfoSheet
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_diag_cancel
import com.hopcape.odo.feature.support.resources.sp_diag_confirm_body
import com.hopcape.odo.feature.support.resources.sp_diag_confirm_send
import com.hopcape.odo.feature.support.resources.sp_diag_confirm_title
import com.hopcape.odo.feature.support.resources.sp_diag_done
import com.hopcape.odo.feature.support.resources.sp_diag_sent_body
import com.hopcape.odo.feature.support.resources.sp_diag_sent_title
import org.jetbrains.compose.resources.stringResource

/** Where the "Send diagnostics" row has got to. */
internal sealed interface DiagnosticsPrompt {

    /** Nothing on screen — the resting state. */
    data object Hidden : DiagnosticsPrompt

    /** Asking, before anything leaves the phone. */
    data object Asking : DiagnosticsPrompt

    /** Asked and answered: [reference] is the code the owner should quote to support. */
    data class Queued(val reference: String) : DiagnosticsPrompt
}

/**
 * The two steps of sending diagnostics by hand: the question, then the code.
 *
 * The question exists because the tap sends data off the phone, and the row's own subtitle is
 * not the place to list what does and does not go. The code exists because an upload nobody
 * can name is an orphan — support gets a file and no way to tell which conversation it
 * belongs to.
 *
 * Sheets, like every other question this app asks — they rise from the thumb and are
 * dismissed by the gesture the owner already uses everywhere else. Both stack over the help
 * sheet that raised them, which stays where it was: the question is about a row on it.
 */
@Composable
internal fun DiagnosticsPromptSheets(
    prompt: DiagnosticsPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        DiagnosticsPrompt.Hidden -> Unit

        DiagnosticsPrompt.Asking -> OdoConfirmSheet(
            title = stringResource(Res.string.sp_diag_confirm_title),
            body = stringResource(Res.string.sp_diag_confirm_body),
            confirmLabel = stringResource(Res.string.sp_diag_confirm_send),
            cancelLabel = stringResource(Res.string.sp_diag_cancel),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            // Sending logs is not destructive. Nothing is lost by saying yes, so the confirm
            // button is the ordinary one.
            destructive = false,
        )

        is DiagnosticsPrompt.Queued -> OdoInfoSheet(
            title = stringResource(Res.string.sp_diag_sent_title),
            body = stringResource(Res.string.sp_diag_sent_body, prompt.reference),
            confirmLabel = stringResource(Res.string.sp_diag_done),
            onDismiss = onDismiss,
        )
    }
}
