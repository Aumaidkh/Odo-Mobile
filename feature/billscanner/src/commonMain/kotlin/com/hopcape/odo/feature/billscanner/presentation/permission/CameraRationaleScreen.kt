package com.hopcape.odo.feature.billscanner.presentation.permission

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssurance
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssuranceKind
import com.hopcape.odo.core.designsystem.component.OdoPermissionBenefit
import com.hopcape.odo.core.designsystem.component.OdoPermissionRationale
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcQr
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_camera_allow
import com.hopcape.odo.feature.billscanner.resources.bs_camera_bills_body
import com.hopcape.odo.feature.billscanner.resources.bs_camera_bills_title
import com.hopcape.odo.feature.billscanner.resources.bs_camera_blocked_subtitle
import com.hopcape.odo.feature.billscanner.resources.bs_camera_no_background
import com.hopcape.odo.feature.billscanner.resources.bs_camera_no_library
import com.hopcape.odo.feature.billscanner.resources.bs_camera_not_now
import com.hopcape.odo.feature.billscanner.resources.bs_camera_only_on_scan
import com.hopcape.odo.feature.billscanner.resources.bs_camera_open_settings
import com.hopcape.odo.feature.billscanner.resources.bs_camera_papers_body
import com.hopcape.odo.feature.billscanner.resources.bs_camera_papers_title
import com.hopcape.odo.feature.billscanner.resources.bs_camera_qr_body
import com.hopcape.odo.feature.billscanner.resources.bs_camera_qr_title
import com.hopcape.odo.feature.billscanner.resources.bs_camera_subtitle
import com.hopcape.odo.feature.billscanner.resources.bs_camera_title
import com.hopcape.odo.feature.billscanner.resources.bs_cd_close
import org.jetbrains.compose.resources.stringResource

/**
 * Odo's own explanation of why it wants the camera, shown before the system prompt.
 *
 * All three scanner jobs are listed together on purpose: the permission is granted once and
 * covers bills, papers and payment codes, so asking for it three times over would be asking
 * for the same thing three times.
 *
 * @param blocked the system will no longer prompt. The primary action becomes a trip to
 *   settings, because a button that reads "Allow camera" and produces no dialog is a button
 *   the owner will press twice and then give up on.
 */
@Composable
internal fun CameraRationaleScreen(
    blocked: Boolean,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoPermissionRationale(
        modifier = modifier,
        icon = IcCamera,
        title = stringResource(Res.string.bs_camera_title),
        subtitle = if (blocked) {
            stringResource(Res.string.bs_camera_blocked_subtitle)
        } else {
            stringResource(Res.string.bs_camera_subtitle)
        },
        benefits = listOf(
            OdoPermissionBenefit(
                icon = IcFileFilled,
                title = stringResource(Res.string.bs_camera_bills_title),
                description = stringResource(Res.string.bs_camera_bills_body),
            ),
            OdoPermissionBenefit(
                icon = IcShieldCheck,
                title = stringResource(Res.string.bs_camera_papers_title),
                description = stringResource(Res.string.bs_camera_papers_body),
            ),
        ),
        assurances = listOf(
            OdoPermissionAssurance(
                text = stringResource(Res.string.bs_camera_no_background),
                kind = OdoPermissionAssuranceKind.Excluded,
            ),
            OdoPermissionAssurance(
                text = stringResource(Res.string.bs_camera_no_library),
                kind = OdoPermissionAssuranceKind.Excluded,
            ),
            OdoPermissionAssurance(
                text = stringResource(Res.string.bs_camera_only_on_scan),
                kind = OdoPermissionAssuranceKind.Included,
            ),
        ),
        confirmLabel = if (blocked) {
            stringResource(Res.string.bs_camera_open_settings)
        } else {
            stringResource(Res.string.bs_camera_allow)
        },
        onConfirm = onAllow,
        dismissLabel = stringResource(Res.string.bs_camera_not_now),
        onDismiss = onNotNow,
        onClose = onClose,
        closeContentDescription = stringResource(Res.string.bs_cd_close),
    )
}

@OdoThemePreviews
@Composable
private fun CameraRationaleScreenPreview() = OdoPreview(padded = false) {
    CameraRationaleScreen(blocked = false, onAllow = {}, onNotNow = {}, onClose = {})
}
