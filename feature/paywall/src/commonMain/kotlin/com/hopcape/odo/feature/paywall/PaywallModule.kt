package com.hopcape.odo.feature.paywall

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.paywall.navigation.PaywallFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the paywall feature. `NavigationManager` comes from `coreNavigationModule`;
 * the `:app` host registers them all.
 *
 * The [PaywallFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host discovers
 * it via `getAll<FeatureEntryProvider>()`. The purchase / entitlement ViewModel joins here
 * once the Razorpay flow lands.
 */
val paywallModule = module {
    single {
        PaywallFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
