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
import com.hopcape.odo.core.designsystem.component.OdoCityField
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
import com.hopcape.odo.feature.profile.resources.pf_city_add
import com.hopcape.odo.feature.profile.resources.pf_city_all
import com.hopcape.odo.feature.profile.resources.pf_city_choose
import com.hopcape.odo.feature.profile.resources.pf_city_enter_hint
import com.hopcape.odo.feature.profile.resources.pf_city_matches
import com.hopcape.odo.feature.profile.resources.pf_city_no_matches
import com.hopcape.odo.feature.profile.resources.pf_city_not_listed
import com.hopcape.odo.feature.profile.resources.pf_city_note
import com.hopcape.odo.feature.profile.resources.pf_city_search_hint
import com.hopcape.odo.feature.profile.resources.pf_city_sheet_subtitle
import com.hopcape.odo.feature.profile.resources.pf_city_sheet_title
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
 * The mobile number is read-only: it is the number auth has verified, and changing it means
 * proving a new one with an OTP, not typing over it here. On a device that never signed in
 * the field stays empty and its placeholder says to sign in.
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
    val cityMatchTemplate = stringResource(Res.string.pf_city_matches)

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
                value = state.phoneNumber.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = stringResource(Res.string.pf_mobile),
                placeholder = stringResource(Res.string.pf_mobile_signed_out),
                helperText = stringResource(Res.string.pf_mobile_note),
                modifier = Modifier.testTag(EditProfileTestTags.MOBILE_FIELD),
            )
            OdoInputField(
                value = state.email.text,
                onValueChange = { onEvent(EditProfileEvent.EmailChanged(it)) },
                label = stringResource(Res.string.pf_email),
                placeholder = stringResource(Res.string.pf_email_hint),
                errorText = state.email.error?.asString(),
                modifier = Modifier.testTag(EditProfileTestTags.EMAIL_FIELD),
            )
            OdoCityField(
                selected = state.selectedCity,
                cities = state.cities,
                onSelect = { onEvent(EditProfileEvent.CityChanged(it.name)) },
                title = stringResource(Res.string.pf_city_sheet_title),
                subtitle = stringResource(Res.string.pf_city_sheet_subtitle, state.cities.size),
                searchPlaceholder = stringResource(Res.string.pf_city_search_hint),
                matchCountLabel = cityMatchTemplate::withCount,
                allSectionLabel = stringResource(Res.string.pf_city_all),
                emptyResultsText = stringResource(Res.string.pf_city_no_matches),
                closeContentDescription = stringResource(Res.string.pf_cd_close),
                label = stringResource(Res.string.pf_city),
                placeholder = stringResource(Res.string.pf_city_choose),
                notListedLabel = stringResource(Res.string.pf_city_not_listed),
                notListedPlaceholder = stringResource(Res.string.pf_city_enter_hint),
                notListedConfirmLabel = stringResource(Res.string.pf_city_add),
                modifier = Modifier.testTag(EditProfileTestTags.CITY_FIELD),
            )
            OdoText(stringResource(Res.string.pf_city_note), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}

/**
 * Fills a `"%1$d ..."` template with [count]. [OdoCityField]'s `matchCountLabel` is a plain
 * `(Int) -> String`, not `@Composable`, since it is called from inside the sheet's own layout,
 * where `stringResource` can't be — so the template is read once in composition and formatted
 * here, the same split `OnboardingChrome.withCount` uses for the car-model picker.
 */
private fun String.withCount(count: Int): String = replace("%1\$d", count.toString())

/** Tags for the fields, which have no unique words of their own once emptied. */
object EditProfileTestTags {
    const val NAME_FIELD: String = "profile_name_field"
    const val MOBILE_FIELD: String = "profile_mobile_field"
    const val EMAIL_FIELD: String = "profile_email_field"
    const val CITY_FIELD: String = "profile_city_field"
}
