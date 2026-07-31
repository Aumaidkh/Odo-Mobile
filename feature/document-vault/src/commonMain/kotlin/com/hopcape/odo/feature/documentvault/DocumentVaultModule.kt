package com.hopcape.odo.feature.documentvault

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.documentvault.domain.usecase.AddDocumentUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.DeleteDocumentUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentVaultUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ReplaceDocumentFileUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.UpdateDocumentUseCase
import com.hopcape.odo.feature.documentvault.navigation.DocumentVaultFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the document-vault feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [DocumentVaultFeatureEntryProvider] is bound to [FeatureEntryProvider] so the
 * host picks it up via `getAll<FeatureEntryProvider>()`. The ViewModels join the use cases
 * below as the screens are built.
 *
 * `DocumentAllowance` comes from `coreDataModule` and `IdGenerator` + `Clock` from
 * `coreCommonModule`. `DocumentRepository` has no binding yet — it arrives with the data
 * slice, so the use cases below cannot be resolved until then. Nothing asks for them until
 * the ViewModels land.
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

    factory { ObserveDocumentVaultUseCase(documents = get(), clock = get()) }
    factory { ObserveDocumentDetailUseCase(documents = get(), files = get(), clock = get()) }
    factory {
        AddDocumentUseCase(
            documents = get(),
            files = get(),
            allowance = get(),
            idGenerator = get(),
            clock = get(),
        )
    }
    factory { UpdateDocumentUseCase(documents = get(), clock = get()) }
    factory { ReplaceDocumentFileUseCase(documents = get(), files = get()) }
    factory { DeleteDocumentUseCase(documents = get(), files = get()) }
}
