package com.hopcape.odo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import com.hopcape.odo.core.designsystem.component.OdoBlockingSheet
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcRefresh
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.shared.resources.Res
import com.hopcape.odo.shared.resources.as_close
import com.hopcape.odo.shared.resources.as_maintenance_detail
import com.hopcape.odo.shared.resources.as_update_detail
import com.hopcape.odo.shared.resources.as_maintenance_message_default
import com.hopcape.odo.shared.resources.as_maintenance_title
import com.hopcape.odo.shared.resources.as_retry
import com.hopcape.odo.shared.resources.as_update_message
import com.hopcape.odo.shared.resources.as_update_now
import com.hopcape.odo.shared.resources.as_update_title
import org.jetbrains.compose.resources.stringResource

/** The Play Store listing [AppBlockedSheet] links to when the build is too old. */
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.hopcape.odo"

/**
 * Whether the app shell should cover its content with [AppBlockedSheet].
 *
 * A plain function next to `shouldRedirectToTripLogged`, for the same reason: the rule is
 * unit-testable on its own even though the composable calling it (`App`) is not.
 */
internal fun shouldBlock(availability: AppAvailability): Boolean = availability is AppAvailability.Blocked

/**
 * The stop the app shell puts over everything else while [AppAvailability] is
 * [AppAvailability.Blocked].
 *
 * A sheet rather than a screen of its own: the app stays visible behind the scrim, so the
 * owner can see what is being held back rather than an empty page. It refuses swipe, scrim and
 * back ([OdoBlockingSheet]), so there is no gesture that reaches the app underneath.
 */
@Composable
internal fun AppBlockedSheet(
    blocked: AppAvailability.Blocked,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    OdoBlockingSheet(modifier = modifier) {
        when (blocked) {
            AppAvailability.Blocked.UpdateRequired -> BlockedSheetContent(
                icon = IcRefresh,
                tint = OdoTheme.colors.accent,
                title = stringResource(Res.string.as_update_title),
                message = stringResource(Res.string.as_update_message),
                detail = stringResource(Res.string.as_update_detail),
            ) {
                OdoButton(
                    stringResource(Res.string.as_update_now),
                    onClick = { uriHandler.openUri(PLAY_STORE_URL) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OdoButton(
                    stringResource(Res.string.as_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    variant = OdoButtonVariant.Tertiary,
                )
            }

            // One button, and it leaves. There is nothing for the owner to retry against —
            // the server decides when maintenance ends, and the app re-asks on its next
            // launch, so closing it *is* the retry. A "Try again" that answers "still down"
            // every time is a button that exists to disappoint.
            is AppAvailability.Blocked.Maintenance -> BlockedSheetContent(
                icon = IcWarning,
                tint = OdoTheme.colors.warning,
                title = stringResource(Res.string.as_maintenance_title),
                message = blocked.message?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.as_maintenance_message_default),
                detail = stringResource(Res.string.as_maintenance_detail),
            ) {
                OdoButton(
                    stringResource(Res.string.as_close),
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Illustration, then the title, then what is happening and what to do about it.
 *
 * Centred and given room on purpose. Both of these stop the app dead, and a stop stated in
 * two cramped lines above a button reads as an error the owner caused. The illustration is
 * most of the height, which is what makes it read as a state the app is in rather than a
 * failure it is reporting.
 */
@Composable
private fun BlockedSheetContent(
    icon: ImageVector,
    tint: Color,
    title: String,
    message: String,
    detail: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        BlockedIllustration(icon, tint)
        OdoText(
            title,
            style = OdoTheme.typography.title,
            color = OdoTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            OdoText(
                message,
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
            // The second sentence is what the first cannot say without getting long: how
            // long this lasts, and that nothing of the owner's is at stake.
            OdoText(
                detail,
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        content = actions,
    )
}

/**
 * The picture: the state's icon inside three widening rings of its own colour.
 *
 * Drawn rather than drawn *from* — this codebase has no illustration assets and composes its
 * pictures (see `OdoSystemHandoff`), and rings scale to any icon, so a third blocked state
 * needs no new artwork.
 */
@Composable
private fun BlockedIllustration(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(RingOuter),
        contentAlignment = Alignment.Center,
    ) {
        Ring(RingOuter, tint, alpha = RingOuterAlpha)
        Ring(RingMiddle, tint, alpha = RingMiddleAlpha)
        Ring(RingInner, tint, alpha = RingInnerAlpha)
        OdoIcon(
            icon,
            contentDescription = null,
            tint = tint,
            size = OdoTheme.iconSizes.large,
        )
    }
}

@Composable
private fun Ring(size: Dp, tint: Color, alpha: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = alpha)),
    )
}

private val RingOuter = 132.dp
private val RingMiddle = 96.dp
private val RingInner = 64.dp
private const val RingOuterAlpha = 0.06f
private const val RingMiddleAlpha = 0.10f
private const val RingInnerAlpha = 0.16f
