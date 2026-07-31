package com.hopcape.odo.feature.documentvault

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.documentvault.domain.usecase.AddDocumentUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.DeleteDocumentUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentVaultUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ReplaceDocumentFileUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.UpdateDocumentUseCase
import com.hopcape.odo.feature.documentvault.navigation.DocumentVaultFeatureEntryProvider
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentViewModel
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailViewModel
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentViewModel
import com.hopcape.odo.feature.documentvault.presentation.success.AddSuccessViewModel
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultViewModel
import androidx.lifecycle.SavedStateHandle
import org.koin.core.module.dsl.viewModel
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

    // A `factory`, not a `single`: one instance covers one visit to the vault, and every
    // screen of that visit shares its flow id.
    factory { DocumentVaultTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { DocumentVaultViewModel(activeCar = get(), observeVault = get(), telemetry = get()) }
    viewModel {
        DocumentDetailViewModel(
            savedStateHandle = get<SavedStateHandle>(),
            observeDetail = get(),
            deleteDocument = get(),
            replaceFile = get(),
            telemetry = get(),
        )
    }
    viewModel {
        AddDocumentViewModel(
            savedStateHandle = get<SavedStateHandle>(),
            addDocument = get(),
            activeCar = get(),
            currentOwner = get(),
            telemetry = get(),
        )
    }
    viewModel {
        ShareDocumentViewModel(
            savedStateHandle = get<SavedStateHandle>(),
            observeDetail = get(),
            telemetry = get(),
        )
    }
    viewModel {
        AddSuccessViewModel(
            savedStateHandle = get<SavedStateHandle>(),
            observeDetail = get(),
            telemetry = get(),
        )
    }
}
