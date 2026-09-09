package com.hopcape.odo.feature.paywall

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.paywall.navigation.PaywallFeatureEntryProvider
import com.hopcape.odo.feature.paywall.presentation.PaywallTelemetry
import com.hopcape.odo.feature.paywall.presentation.PaywallTrigger
import com.hopcape.odo.feature.paywall.presentation.PaywallViewModel
import com.hopcape.odo.feature.paywall.presentation.onetime.OneTimeContext
import com.hopcape.odo.feature.paywall.presentation.onetime.OneTimeOffersViewModel
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
    // A factory, so one instance covers one visit and its trace id names that visit.
    factory { PaywallTelemetry(logger = get(), analytics = get(), ids = get()) }

    single {
        PaywallFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    // Takes the one-time purchaser rather than the subscription one: different products,
    // different lifetimes, and restoring a consumable would be wrong.
    viewModel { (context: OneTimeContext) ->
        OneTimeOffersViewModel(
            context = context,
            purchaser = get(),
            reconciler = get(),
            telemetry = get(),
        )
    }

    viewModel { (trigger: PaywallTrigger, amountPaise: Long, freeScans: Int) ->
        PaywallViewModel(
            catalog = get(),
            purchaser = get(),
            telemetry = get(),
            trigger = trigger,
            amountPaise = amountPaise,
            freeScans = freeScans,
        )
    }
}
