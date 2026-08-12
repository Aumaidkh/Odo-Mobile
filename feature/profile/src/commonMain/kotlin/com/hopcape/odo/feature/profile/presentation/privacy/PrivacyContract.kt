package com.hopcape.odo.feature.profile.presentation.privacy

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/** Which switch the owner moved on the privacy screen. */
internal sealed interface PrivacyEvent {

    /** Let this owner's prices feed the city benchmark. Stored on the profile, so it syncs. */
    data class SharePricesToggled(val enabled: Boolean) : PrivacyEvent

    /** Keep trip coordinates, or only the distance. */
    data class KeepTripRoutesToggled(val enabled: Boolean) : PrivacyEvent

    /** Product analytics consent. */
    data class UsageAnalyticsToggled(val enabled: Boolean) : PrivacyEvent
}

/**
 * Display state for the privacy screen — the three switches and nothing about permissions.
 *
 * Device access is deliberately absent. Reading a runtime permission needs the thing hosting
 * the UI, which is why `rememberPermissionController` is a composable rather than an injected
 * port; a copy of its answer in here would be a second source of truth that goes stale the
 * moment the owner changes something in system settings.
 *
 * [error] is set when a write fails. The switches keep showing what is *stored*, so a failed
 * toggle springs back rather than lying about being on — the same rule as the notifications
 * screen, and it matters more here, where the lie would be about privacy.
 */
@Immutable
internal data class PrivacyUiState(
    val sharePrices: Boolean = true,
    val keepTripRoutes: Boolean = false,
    val usageAnalytics: Boolean = true,
    val error: UiText? = null,
)
