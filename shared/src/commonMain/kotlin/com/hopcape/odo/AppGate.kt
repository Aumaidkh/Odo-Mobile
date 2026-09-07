package com.hopcape.odo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
internal fun AppBlockedSheet(blocked: AppAvailability.Blocked, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    OdoBlockingSheet(modifier = modifier) {
        when (blocked) {
            AppAvailability.Blocked.UpdateRequired -> BlockedSheetContent(
                icon = { BlockedIcon(IcRefresh) },
                title = stringResource(Res.string.as_update_title),
                message = stringResource(Res.string.as_update_message),
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

            is AppAvailability.Blocked.Maintenance -> BlockedSheetContent(
                icon = { BlockedIcon(IcWarning) },
                title = stringResource(Res.string.as_maintenance_title),
                message = blocked.message?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.as_maintenance_message_default),
            ) {
                OdoButton(
                    stringResource(Res.string.as_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Icon, then the two lines, then the buttons — the layout both blocked states share. */
@Composable
private fun BlockedSheetContent(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    icon()
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        OdoText(title, style = OdoTheme.typography.title, color = OdoTheme.colors.text)
        OdoText(message, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        content = actions,
    )
}

@Composable
private fun BlockedIcon(icon: ImageVector) {
    OdoIcon(
        icon,
        contentDescription = null,
        tint = OdoTheme.colors.warning,
        size = OdoTheme.iconSizes.large,
    )
}
