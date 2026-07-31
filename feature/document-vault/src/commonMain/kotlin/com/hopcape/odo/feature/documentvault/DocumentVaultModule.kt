package com.hopcape.odo.feature.documentvault

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.documentvault.navigation.DocumentVaultFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the document-vault feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [DocumentVaultFeatureEntryProvider] is bound to [FeatureEntryProvider] so the
 * host picks it up via `getAll<FeatureEntryProvider>()`. The vault ViewModel + the
 * document use cases join here as the feature is built.
 *
 * The `DocumentFileStore` binding is deliberately *not* here: storing a file needs
 * platform APIs (a Context on Android), so each platform contributes its own —
 * `documentVaultAndroidModule` / `documentVaultIosModule` — through the bootstrap's
 * platform module.
 */
val documentVaultModule = module {
    single {
        DocumentVaultFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
