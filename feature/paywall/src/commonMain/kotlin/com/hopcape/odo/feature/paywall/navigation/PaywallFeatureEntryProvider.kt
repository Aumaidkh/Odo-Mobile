package com.hopcape.odo.feature.paywall.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.feature.paywall.presentation.PaywallEffect
import com.hopcape.odo.feature.paywall.presentation.PaywallScreen
import com.hopcape.odo.feature.paywall.presentation.PaywallTrigger
import com.hopcape.odo.feature.paywall.presentation.PaywallViewModel
import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel

/**
 * Paywall's contribution to the navigation graph: the [OdoDestination.Paywall] screen, framed
 * by the trigger carried on the key. Collected by the `:app` host via
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
 * The paywall route host.
 *
 * The framing arrives on the key as primitives — `:core:navigation` stays domain-free — and is
 * turned into a [PaywallTrigger] here. An unrecognised one becomes [PaywallTrigger.GENERIC]
 * rather than a crash: a deep link is not a promise, and the generic framing sells the same
 * thing.
 */
@Composable
internal fun PaywallRoute(
    key: OdoDestination.Paywall,
    navigationManager: NavigationManager,
) {
    val trigger = PaywallTrigger.entries.firstOrNull { it.name == key.trigger } ?: PaywallTrigger.GENERIC
    val viewModel = koinViewModel<PaywallViewModel> {
        parametersOf(trigger, key.amountPaise, key.freeScans)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // Closing and having bought end the same way. A purchase has already unlocked the
            // screen underneath through the entitlement stream, so the right thing to do with
            // the paywall is get it out of the way.
            PaywallEffect.GoBack -> navigationManager.back()
        }
    }

    PaywallScreen(state = state, onEvent = viewModel::onEvent)
}
