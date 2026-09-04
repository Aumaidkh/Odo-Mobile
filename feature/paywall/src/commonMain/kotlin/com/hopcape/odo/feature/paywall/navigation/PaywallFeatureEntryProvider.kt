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
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.paywall.presentation.onetime.OneTimeOffersEffect
import com.hopcape.odo.feature.paywall.presentation.onetime.OneTimeOffersSheetContent
import com.hopcape.odo.feature.paywall.presentation.onetime.OneTimeOffersViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Paywall's contribution to the navigation graph: the [OdoDestination.Paywall.Plans] screen,
 * framed by the trigger carried on the key, plus the [OdoDestination.Paywall.OneTimeOffers]
 * sheet it can open. Collected by the `:app` host via `getAll<FeatureEntryProvider>()`.
 */
internal class PaywallFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Paywall.Plans> { key -> PaywallRoute(key, navigationManager) }
        entry<OdoDestination.Paywall.OneTimeOffers>(
            metadata = ModalBottomSheetSceneStrategy.bottomSheet(),
        ) {
            OneTimeOffersRoute(navigationManager)
        }
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
    key: OdoDestination.Paywall.Plans,
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
            PaywallEffect.OpenOneTimeOffers ->
                navigationManager.navigateTo(OdoDestination.Paywall.OneTimeOffers)
        }
    }

    PaywallScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The one-time offers sheet.
 *
 * A sheet rather than a screen, so closing it puts the owner back on the plans they were
 * reading rather than out of the paywall entirely.
 */
@Composable
internal fun OneTimeOffersRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<OneTimeOffersViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OneTimeOffersEffect.Dismiss -> navigationManager.back()
        }
    }

    OneTimeOffersSheetContent(state = state, onEvent = viewModel::onEvent)
}
