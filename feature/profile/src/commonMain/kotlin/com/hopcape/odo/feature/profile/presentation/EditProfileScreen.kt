package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoDropdownField
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.platform.file.FileTypes
import com.hopcape.odo.core.platform.file.rememberFilePicker
import com.hopcape.odo.feature.profile.presentation.state.text
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_close
import com.hopcape.odo.feature.profile.resources.pf_change_photo
import com.hopcape.odo.feature.profile.resources.pf_city
import com.hopcape.odo.feature.profile.resources.pf_city_note
import com.hopcape.odo.feature.profile.resources.pf_delete
import com.hopcape.odo.feature.profile.resources.pf_deleting
import com.hopcape.odo.feature.profile.resources.pf_edit_title
import com.hopcape.odo.feature.profile.resources.pf_email
import com.hopcape.odo.feature.profile.resources.pf_email_hint
import com.hopcape.odo.feature.profile.resources.pf_full_name
import com.hopcape.odo.feature.profile.resources.pf_mobile
import com.hopcape.odo.feature.profile.resources.pf_mobile_note
import com.hopcape.odo.feature.profile.resources.pf_mobile_signed_out
import com.hopcape.odo.feature.profile.resources.pf_save
import org.jetbrains.compose.resources.stringResource

/**
 * Edit-profile full screen.
 *
 * The city field is the one that matters beyond this screen: it is what turns price
 * benchmarks on, and onboarding never asks for it.
 *
 * The mobile number is read-only and empty until auth lands — there is no session and
 * therefore no number to show. It stays on the screen because that is where an owner will
 * look for it once there is one.
 */
@Composable
internal fun EditProfileScreen(
    state: EditProfileUiState,
    onEvent: (EditProfileEvent) -> Unit,
    onClose: () -> Unit,
) {
    val pickPhoto = rememberFilePicker(mimeTypes = FileTypes.PHOTOS) { picked ->
        if (picked != null) onEvent(EditProfileEvent.PhotoPicked(picked))
    }

    OdoScreen(
        topBar = { CloseTopBar(stringResource(Res.string.pf_edit_title), stringResource(Res.string.pf_cd_close), onClose) },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.screenEdge).padding(vertical = OdoTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                state.submission.error?.let { message ->
                    OdoText(
                        message.asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OdoButton(
                    stringResource(Res.string.pf_save),
                    onClick = { onEvent(EditProfileEvent.Save) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave,
                )
                OdoText(
                    if (state.deletion.isInFlight) {
                        stringResource(Res.string.pf_deleting)
                    } else {
                        stringResource(Res.string.pf_delete)
                    },
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.deletion.isInFlight) {
                            onEvent(EditProfileEvent.DeleteRequested)
                        }
                        .padding(vertical = OdoTheme.spacing.xs),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(
                        initial = state.name.text.trim().firstOrNull()?.uppercase() ?: "O",
                        photoKey = state.avatarPath,
                        size = 88.dp,
                        onClick = pickPhoto,
                    )
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(OdoTheme.colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        OdoIcon(IcCamera, contentDescription = null, tint = OdoTheme.colors.onAccent, size = OdoTheme.iconSizes.small)
                    }
                }
                OdoText(
                    stringResource(Res.string.pf_change_photo),
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.accent,
                    modifier = Modifier.clickable(onClick = pickPhoto),
                )
            }

            OdoInputField(
                value = state.name.text,
                onValueChange = { onEvent(EditProfileEvent.NameChanged(it)) },
                label = stringResource(Res.string.pf_full_name),
                errorText = state.name.error?.asString(),
                modifier = Modifier.testTag(EditProfileTestTags.NAME_FIELD),
            )
            OdoInputField(
                value = "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = stringResource(Res.string.pf_mobile),
                placeholder = stringResource(Res.string.pf_mobile_signed_out),
                helperText = stringResource(Res.string.pf_mobile_note),
            )
            OdoInputField(
                value = state.email.text,
                onValueChange = { onEvent(EditProfileEvent.EmailChanged(it)) },
                label = stringResource(Res.string.pf_email),
                placeholder = stringResource(Res.string.pf_email_hint),
                errorText = state.email.error?.asString(),
                modifier = Modifier.testTag(EditProfileTestTags.EMAIL_FIELD),
            )
            OdoDropdownField(
                selected = state.city.value,
                options = state.cities,
                onSelect = { onEvent(EditProfileEvent.CityChanged(it)) },
                label = stringResource(Res.string.pf_city),
                modifier = Modifier.testTag(EditProfileTestTags.CITY_FIELD),
            )
            OdoText(stringResource(Res.string.pf_city_note), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }

    if (state.confirmingDelete) {
        DeleteDataDialog(
            onConfirm = { onEvent(EditProfileEvent.DeleteConfirmed) },
            onDismiss = { onEvent(EditProfileEvent.DeleteDismissed) },
        )
    }
}

/** Tags for the three fields, which have no unique words of their own once emptied. */
object EditProfileTestTags {
    const val NAME_FIELD: String = "profile_name_field"
    const val EMAIL_FIELD: String = "profile_email_field"
    const val CITY_FIELD: String = "profile_city_field"
}
