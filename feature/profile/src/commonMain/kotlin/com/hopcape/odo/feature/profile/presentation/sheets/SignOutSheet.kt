package com.hopcape.odo.feature.profile.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcSignOut
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.presentation.IconTile
import com.hopcape.odo.feature.profile.presentation.ProfileSheet
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cancel
import com.hopcape.odo.feature.profile.resources.pf_sign_out
import com.hopcape.odo.feature.profile.resources.pf_signout_body
import com.hopcape.odo.feature.profile.resources.pf_signout_title
import org.jetbrains.compose.resources.stringResource

/**
 * Sign-out confirmation ([com.hopcape.odo.core.navigation.OdoDestination.Profile.SignOut]).
 * Shown as a sheet; [onSignOut] is the destructive confirm, [onCancel]/swipe-down dismiss.
 */
@Composable
internal fun SignOutSheetContent(onSignOut: () -> Unit, onCancel: () -> Unit) {
    ProfileSheet {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            IconTile(IcSignOut, tint = OdoTheme.colors.danger, size = 56.dp)
            OdoText(stringResource(Res.string.pf_signout_title), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
            OdoText(stringResource(Res.string.pf_signout_body), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim, textAlign = TextAlign.Center)
        }
        OdoButton(stringResource(Res.string.pf_sign_out), onClick = onSignOut, modifier = Modifier.fillMaxWidth(), variant = OdoButtonVariant.Danger)
        OdoButton(stringResource(Res.string.pf_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth(), variant = OdoButtonVariant.Tertiary)
    }
}
