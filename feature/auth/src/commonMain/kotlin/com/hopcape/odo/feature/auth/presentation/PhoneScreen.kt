package com.hopcape.odo.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoPhoneNumberDefaults
import com.hopcape.odo.core.designsystem.component.OdoPhoneNumberField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcLock
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.auth.resources.Res
import com.hopcape.odo.feature.auth.resources.au_cd_back
import com.hopcape.odo.feature.auth.resources.au_phone_hint
import com.hopcape.odo.feature.auth.resources.au_phone_label
import com.hopcape.odo.feature.auth.resources.au_phone_note
import com.hopcape.odo.feature.auth.resources.au_phone_subtitle
import com.hopcape.odo.feature.auth.resources.au_phone_title
import com.hopcape.odo.feature.auth.resources.au_send_code
import com.hopcape.odo.feature.auth.resources.au_skip
import com.hopcape.odo.feature.auth.resources.au_skip_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Phone-number entry ([com.hopcape.odo.core.navigation.OdoDestination.Auth.Phone]).
 *
 * Entered *after* car setup, so the owner already has something to protect — which is why
 * the copy talks about securing records rather than creating an account, and why
 * [onSkip] is a first-class action rather than a hidden escape.
 *
 * The field is [OdoPhoneNumberField] from the design system, driven by the platform
 * keyboard; it takes focus on arrival so the keyboard is already up.
 */
@Composable
internal fun PhoneScreen(
    onBack: () -> Unit,
    onSendCode: (phone: String) -> Unit,
    onSkip: () -> Unit,
) {
    // TODO(auth): hoist to a ViewModel once Supabase phone auth lands — the screen
    //  shape does not change, only where `phone` lives.
    var phone by remember { mutableStateOf("") }
    val isComplete = phone.length == OdoPhoneNumberDefaults.MaxLength

    OdoScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                // Keep the CTA above the keyboard that this screen deliberately raises.
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(OdoTheme.spacing.sm))
            OdoCircularIconButton(
                IcArrowLeft,
                contentDescription = stringResource(Res.string.au_cd_back),
                onClick = onBack,
                variant = OdoCircularIconButtonVariant.Raised,
            )
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            OdoText(
                stringResource(Res.string.au_phone_title),
                style = OdoTheme.typography.title.copy(fontSize = 28.sp, lineHeight = 34.sp),
            )
            Spacer(Modifier.height(OdoTheme.spacing.xs))
            OdoText(
                stringResource(Res.string.au_phone_subtitle),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )

            Spacer(Modifier.height(OdoTheme.spacing.xl))
            OdoPhoneNumberField(
                value = phone,
                onValueChange = { input -> phone = input },
                label = stringResource(Res.string.au_phone_label),
                placeholder = stringResource(Res.string.au_phone_hint),
                requestFocus = true,
            )

            Spacer(Modifier.height(OdoTheme.spacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OdoIcon(IcLock, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.small)
                OdoText(
                    stringResource(Res.string.au_phone_note),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }

            Spacer(Modifier.height(OdoTheme.spacing.lg))
            OdoButton(
                stringResource(Res.string.au_send_code),
                onClick = { onSendCode(phone) },
                enabled = isComplete,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(OdoTheme.spacing.sm))
            OdoButton(
                stringResource(Res.string.au_skip),
                onClick = onSkip,
                variant = OdoButtonVariant.Tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(OdoTheme.spacing.xs))
            OdoText(
                stringResource(Res.string.au_skip_hint),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
            )
            Spacer(Modifier.height(OdoTheme.spacing.xl))
        }
    }
}
