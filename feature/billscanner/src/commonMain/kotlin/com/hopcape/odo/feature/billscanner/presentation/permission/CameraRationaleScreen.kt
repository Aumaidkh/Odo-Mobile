package com.hopcape.odo.feature.billscanner.presentation.permission

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssurance
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssuranceKind
import com.hopcape.odo.core.designsystem.component.OdoPermissionRationale
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcImage
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_camera_allow
import com.hopcape.odo.feature.billscanner.resources.bs_camera_blocked_subtitle
import com.hopcape.odo.feature.billscanner.resources.bs_camera_gallery
import com.hopcape.odo.feature.billscanner.resources.bs_camera_not_now
import com.hopcape.odo.feature.billscanner.resources.bs_camera_open_settings
import com.hopcape.odo.feature.billscanner.resources.bs_camera_subtitle
import com.hopcape.odo.feature.billscanner.resources.bs_camera_tick_fast
import com.hopcape.odo.feature.billscanner.resources.bs_camera_tick_printed
import com.hopcape.odo.feature.billscanner.resources.bs_camera_tick_private
import com.hopcape.odo.feature.billscanner.resources.bs_camera_title
import com.hopcape.odo.feature.billscanner.resources.bs_cd_close
import com.hopcape.odo.feature.billscanner.resources.bs_no_camera_body
import com.hopcape.odo.feature.billscanner.resources.bs_no_camera_settings
import com.hopcape.odo.feature.billscanner.resources.bs_no_camera_title
import org.jetbrains.compose.resources.stringResource

/**
 * Odo's explanation of why it wants the camera, shown before the system prompt (S5).
 * "Choose from gallery" is offered in every state, because the system photo picker needs no
 * permission.
 *
 * @param blocked the system will no longer prompt, so the primary action opens settings.
 */
@Composable
internal fun CameraRationaleScreen(
    blocked: Boolean,
    onAllow: () -> Unit,
    onGallery: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoPermissionRationale(
        modifier = modifier,
        icon = IcCamera,
        title = stringResource(Res.string.bs_camera_title),
        subtitle = stringResource(if (blocked) Res.string.bs_camera_blocked_subtitle else Res.string.bs_camera_subtitle),
        benefits = emptyList(),
        assurances = listOf(
            Res.string.bs_camera_tick_printed,
            Res.string.bs_camera_tick_private,
            Res.string.bs_camera_tick_fast,
        ).map { OdoPermissionAssurance(text = stringResource(it), kind = OdoPermissionAssuranceKind.Included) },
        confirmLabel = stringResource(if (blocked) Res.string.bs_camera_open_settings else Res.string.bs_camera_allow),
        onConfirm = onAllow,
        secondaryLabel = stringResource(Res.string.bs_camera_gallery),
        onSecondary = onGallery,
        dismissLabel = stringResource(Res.string.bs_camera_not_now),
        onDismiss = onNotNow,
        onClose = onClose,
        closeContentDescription = stringResource(Res.string.bs_cd_close),
    )
}

/** The scanner after the camera was declined (S6): the gallery is the way forward. */
@Composable
internal fun GalleryOnlyScreen(
    onGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoPermissionRationale(
        modifier = modifier,
        icon = IcImage,
        title = stringResource(Res.string.bs_no_camera_title),
        subtitle = stringResource(Res.string.bs_no_camera_body),
        benefits = emptyList(),
        assurances = emptyList(),
        confirmLabel = stringResource(Res.string.bs_camera_gallery),
        onConfirm = onGallery,
        dismissLabel = stringResource(Res.string.bs_no_camera_settings),
        onDismiss = onOpenSettings,
        onClose = onClose,
        closeContentDescription = stringResource(Res.string.bs_cd_close),
    )
}

@OdoThemePreviews
@Composable
private fun CameraRationaleScreenPreview() = OdoPreview(padded = false) {
    CameraRationaleScreen(blocked = false, onAllow = {}, onGallery = {}, onNotNow = {}, onClose = {})
}

@OdoThemePreviews
@Composable
private fun CameraRationaleBlockedPreview() = OdoPreview(padded = false) {
    CameraRationaleScreen(blocked = true, onAllow = {}, onGallery = {}, onNotNow = {}, onClose = {})
}

@OdoThemePreviews
@Composable
private fun GalleryOnlyScreenPreview() = OdoPreview(padded = false) {
    GalleryOnlyScreen(onGallery = {}, onOpenSettings = {}, onClose = {})
}
