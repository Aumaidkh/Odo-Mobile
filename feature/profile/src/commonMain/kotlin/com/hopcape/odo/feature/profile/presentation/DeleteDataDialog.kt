package com.hopcape.odo.feature.profile.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hopcape.odo.core.designsystem.component.OdoConfirmDialog
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cancel
import com.hopcape.odo.feature.profile.resources.pf_delete_body
import com.hopcape.odo.feature.profile.resources.pf_delete_confirm
import com.hopcape.odo.feature.profile.resources.pf_delete_title
import org.jetbrains.compose.resources.stringResource

/**
 * The confirmation shown before the local wipe.
 *
 * The body says what actually goes and that nothing has been backed up, because the owner
 * cannot get any of it back and the app cannot fetch it from anywhere.
 */
@Composable
internal fun DeleteDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OdoConfirmDialog(
        title = stringResource(Res.string.pf_delete_title),
        body = stringResource(Res.string.pf_delete_body),
        confirmLabel = stringResource(Res.string.pf_delete_confirm),
        cancelLabel = stringResource(Res.string.pf_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmModifier = Modifier.testTag(ProfileTestTags.DELETE_CONFIRM),
    )
}
