package com.hopcape.odo.feature.paywall.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.feature.paywall.presentation.PaywallScreen
import com.hopcape.odo.feature.paywall.presentation.PaywallTrigger
import com.hopcape.odo.feature.paywall.presentation.PaywallUiState

/**
 * Paywall's contribution to the navigation graph: the [OdoDestination.Paywall] screen,
 * framed by the trigger carried on the key. Collected by the `:app` host via
 * `getAll<FeatureEntryProvider>()`.
 */
internal class PaywallFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Paywall> { key -> PaywallRoute(key, navigationManager) }
    }
}

/**
 * The paywall route host — maps the typed key's [OdoDestination.Paywall.trigger] onto the
 * screen's framing and holds the plan selection locally until the entitlement + Razorpay
 * flow lands. Close pops back; Start Pro / Restore / Build passport are stubs for now.
 */
@Composable
internal fun PaywallRoute(
    key: OdoDestination.Paywall,
    navigationManager: NavigationManager,
) {
    val trigger = runCatching { PaywallTrigger.valueOf(key.trigger) }.getOrDefault(PaywallTrigger.GENERIC)
    var state by remember {
        mutableStateOf(
            PaywallUiState(
                trigger = trigger,
                amountPaise = key.amountPaise,
                freeScans = if (key.freeScans > 0) key.freeScans else 3,
            ),
        )
    }
    PaywallScreen(
        state = state,
        onSelectPlan = { state = state.copy(selectedPlan = it) },
        onStartPro = { /* TODO: launch Razorpay checkout for the selected plan. */ },
        onRestore = { /* TODO: restore purchases. */ },
        onBuildPassport = { /* TODO: open the Resale Passport purchase flow. */ },
        onClose = { navigationManager.back() },
    )
}
