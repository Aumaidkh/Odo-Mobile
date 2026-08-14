package com.hopcape.odo.feature.paywall

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.paywall.navigation.PaywallFeatureEntryProvider
import com.hopcape.odo.feature.paywall.presentation.PaywallTrigger
import com.hopcape.odo.feature.paywall.presentation.PaywallViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the paywall feature. `NavigationManager` comes from `coreNavigationModule`;
 * `SubscriptionCatalog` and `SubscriptionPurchaser` come from `:infrastructure:billing`, or
 * from its unconfigured stand-ins on a build with no store key. The `:app` host registers
 * them all.
 *
 * The [PaywallFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host discovers
 * it via `getAll<FeatureEntryProvider>()`.
 *
 * The ViewModel takes its framing as parameters rather than reading a saved-state handle: the
 * trigger, the savings figure and the free-scan cap all arrive on the navigation key, and
 * passing them in keeps the ViewModel free of any idea of how it was navigated to.
 */
val paywallModule = module {
    single {
        PaywallFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    viewModel { (trigger: PaywallTrigger, amountPaise: Long, freeScans: Int) ->
        PaywallViewModel(
            catalog = get(),
            purchaser = get(),
            trigger = trigger,
            amountPaise = amountPaise,
            freeScans = freeScans,
        )
    }
}
