package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcQr
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** One thing the owner gets by granting the permission. */
@Immutable
data class OdoPermissionBenefit(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/** Whether an assurance line is something the app will not do, or something it will. */
enum class OdoPermissionAssuranceKind {

    /** A thing the app does **not** do. Rendered with a cross. */
    Excluded,

    /** A thing the app does, stated as a limit. Rendered with a tick. */
    Included,
}

/** One line of the "here is what this does and does not allow" card. */
@Immutable
data class OdoPermissionAssurance(
    val text: String,
    val kind: OdoPermissionAssuranceKind,
)

/**
 * Asks for a system permission in the app's own words, before the system dialog appears.
 *
 * Shown first because the system prompt is one sentence the app does not control, and on iOS
 * it is offered exactly once. An owner who understands what the permission is for before they
 * see it is far more likely to grant it, and one who declines here has declined a screen that
 * can be shown again rather than a prompt that cannot.
 *
 * Every string is the caller's. The layout is fixed so the app asks for camera, notifications
 * and anything later in a recognisably identical way.
 *
 * ```
 * OdoPermissionRationale(
 *     icon = IcCamera,
 *     title = stringResource(Res.string.bs_camera_title),
 *     subtitle = stringResource(Res.string.bs_camera_subtitle),
 *     benefits = benefits,
 *     assurances = assurances,
 *     confirmLabel = stringResource(Res.string.bs_camera_allow),
 *     onConfirm = vm::allow,
 *     dismissLabel = stringResource(Res.string.bs_camera_not_now),
 *     onDismiss = vm::notNow,
 *     onClose = vm::close,
 *     closeContentDescription = stringResource(Res.string.bs_cd_close),
 * )
 * ```
 *
 * @param confirmLabel the primary action. Say what will happen ("Allow camera"), and change it
 *   to the settings wording when the system will no longer prompt.
 */
@Composable
fun OdoPermissionRationale(
    icon: ImageVector,
    title: String,
    subtitle: String,
    benefits: List<OdoPermissionBenefit>,
    assurances: List<OdoPermissionAssurance>,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    closeContentDescription: String?,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OdoTheme.spacing.screenEdge,
                        vertical = OdoTheme.spacing.sm,
                    ),
            ) {
                OdoCircularIconButton(
                    imageVector = IcClose,
                    contentDescription = closeContentDescription,
                    onClick = onClose,
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OdoTheme.spacing.screenEdge,
                        vertical = OdoTheme.spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                OdoButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    variant = OdoButtonVariant.Tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        // Scrollable because the benefit list grows and the owner's text size can double.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
                IconTile(icon = icon, size = HERO_TILE, iconSize = OdoTheme.iconSizes.large)
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    OdoText(text = title, style = OdoTheme.typography.title)
                    OdoText(
                        text = subtitle,
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
                benefits.forEach { benefit -> BenefitRow(benefit) }
            }

            if (assurances.isNotEmpty()) {
                OdoCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                        assurances.forEach { assurance -> AssuranceRow(assurance) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(benefit: OdoPermissionBenefit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        IconTile(
            icon = benefit.icon,
            size = BENEFIT_TILE,
            iconSize = OdoTheme.iconSizes.medium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(text = benefit.title, style = OdoTheme.typography.heading)
            OdoText(
                text = benefit.description,
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun AssuranceRow(assurance: OdoPermissionAssurance) {
    val included = assurance.kind == OdoPermissionAssuranceKind.Included
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(
            imageVector = if (included) IcCheck else IcClose,
            contentDescription = null,
            tint = if (included) OdoTheme.colors.success else OdoTheme.colors.danger,
            size = OdoTheme.iconSizes.small,
        )
        OdoText(
            text = assurance.text,
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** The rounded square an icon sits in — the hero at the top and one per benefit row. */
@Composable
private fun IconTile(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(OdoTheme.shapes.field)
            .background(OdoTheme.colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(
            imageVector = icon,
            contentDescription = null,
            tint = OdoTheme.colors.accent,
            size = iconSize,
        )
    }
}

/**
 * A one-line reminder that a permission is still missing, shown on the screen that needs it.
 *
 * The counterpart to [OdoPermissionRationale] for an owner who chose not to grant. Without it
 * the screen is a dead end: the feature does not work and nothing on screen says why or offers
 * a way back. Small on purpose — a full-screen rationale shown twice reads as nagging.
 *
 * @param actionLabel what the button does. "Allow" when the system will still prompt,
 *   "Open settings" when it will not.
 */
@Composable
fun OdoPermissionNudge(
    icon: ImageVector,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoCard(
        modifier = modifier,
        color = OdoTheme.colors.surfaceRaised,
        contentPadding = PaddingValues(
            start = OdoTheme.spacing.md,
            end = OdoTheme.spacing.sm,
            top = OdoTheme.spacing.sm,
            bottom = OdoTheme.spacing.sm,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                imageVector = icon,
                contentDescription = null,
                tint = OdoTheme.colors.accent,
                size = OdoTheme.iconSizes.medium,
            )
            OdoText(
                text = message,
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            OdoButton(
                text = actionLabel,
                onClick = onAction,
                variant = OdoButtonVariant.Tertiary,
            )
        }
    }
}

private val HERO_TILE = 72.dp
private val BENEFIT_TILE = 44.dp

@OdoThemePreviews
@Composable
private fun OdoPermissionRationalePreview() = OdoPreview(padded = false) {
    OdoPermissionRationale(
        icon = IcCamera,
        title = "Odo needs your camera to read things",
        subtitle = "Everything Odo checks starts as a photo. Grant once and all three work.",
        benefits = listOf(
            OdoPermissionBenefit(
                icon = IcFileFilled,
                title = "Service bills",
                description = "Read the amounts and check them against real city prices.",
            ),
            OdoPermissionBenefit(
                icon = IcShieldCheck,
                title = "Insurance, PUC & RC",
                description = "Pull the expiry date so renewals never catch you out.",
            ),
            OdoPermissionBenefit(
                icon = IcQr,
                title = "Fuel pump QR",
                description = "Pay by UPI and log the fill in one step.",
            ),
        ),
        assurances = listOf(
            OdoPermissionAssurance(
                text = "No photos or video in the background",
                kind = OdoPermissionAssuranceKind.Excluded,
            ),
            OdoPermissionAssurance(
                text = "No access to your photo library",
                kind = OdoPermissionAssuranceKind.Excluded,
            ),
            OdoPermissionAssurance(
                text = "Only when you tap Scan",
                kind = OdoPermissionAssuranceKind.Included,
            ),
        ),
        confirmLabel = "Allow camera",
        onConfirm = {},
        dismissLabel = "Not now",
        onDismiss = {},
        onClose = {},
        closeContentDescription = "Close",
    )
}
