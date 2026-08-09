package com.hopcape.odo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
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

/** The Play Store listing [AppBlockedScreen.UpdateRequired] links to. */
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.hopcape.odo"

/**
 * Whether the app shell should show [AppBlockedScreen] instead of its normal content.
 *
 * A plain function next to `shouldRedirectToTripLogged`, for the same reason: the rule is
 * unit-testable on its own even though the composable calling it (`App`) is not.
 */
internal fun shouldBlock(availability: AppAvailability): Boolean = availability is AppAvailability.Blocked

/**
 * The full-screen stop the app shell renders in place of everything else while
 * [AppAvailability] is [AppAvailability.Blocked] — no route, deep link, or pending redirect
 * can navigate past it, because there is nothing else on screen to navigate to.
 *
 * Rendered inside [com.hopcape.odo.core.designsystem.theme.OdoTheme] by its caller, so it is
 * branded and honours the owner's dark/light setting even though sign-in and onboarding
 * haven't run yet at this point in the composition.
 */
@Composable
internal fun AppBlockedScreen(blocked: AppAvailability.Blocked, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier.fillMaxSize().background(OdoTheme.colors.bg).statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        when (blocked) {
            AppAvailability.Blocked.UpdateRequired -> OdoEmptyState(
                title = stringResource(Res.string.as_update_title),
                message = stringResource(Res.string.as_update_message),
                icon = {
                    OdoIcon(
                        IcRefresh,
                        contentDescription = null,
                        tint = OdoTheme.colors.warning,
                        size = OdoTheme.iconSizes.large,
                    )
                },
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                        OdoButton(
                            stringResource(Res.string.as_update_now),
                            onClick = { uriHandler.openUri(PLAY_STORE_URL) },
                        )
                        OdoButton(
                            stringResource(Res.string.as_retry),
                            onClick = onRetry,
                            variant = OdoButtonVariant.Tertiary,
                        )
                    }
                },
            )

            is AppAvailability.Blocked.Maintenance -> OdoEmptyState(
                title = stringResource(Res.string.as_maintenance_title),
                message = blocked.message?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.as_maintenance_message_default),
                icon = {
                    OdoIcon(
                        IcWarning,
                        contentDescription = null,
                        tint = OdoTheme.colors.warning,
                        size = OdoTheme.iconSizes.large,
                    )
                },
                action = { OdoButton(stringResource(Res.string.as_retry), onClick = onRetry) },
            )
        }
    }
}
