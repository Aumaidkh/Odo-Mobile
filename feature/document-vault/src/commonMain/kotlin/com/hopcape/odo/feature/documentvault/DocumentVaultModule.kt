package com.hopcape.odo.feature.documentvault

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import com.hopcape.odo.feature.documentvault.domain.file.PlatformDocumentFileStore
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
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
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
 * `DocumentRepository` and `DocumentAllowance` come from `coreDataModule`, `IdGenerator` +
 * `Clock` from `coreCommonModule`.
 *
 * `PlatformFileStore` comes from `:core:platform`, bound per platform by
 * `corePlatformAndroidModule` / `corePlatformIosModule` in the bootstrap's platform module.
 * The vault's own [DocumentFileStore] is named on top of it here, in common code, because
 * only the naming is vault-specific — the copying is not.
 */
val documentVaultModule = module {
    single {
        DocumentVaultFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    single<DocumentFileStore> { PlatformDocumentFileStore(files = get()) }

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
    viewModel { params ->
        DocumentDetailViewModel(
            documentId = params.get<DocumentId>(),
            observeDetail = get(),
            deleteDocument = get(),
            replaceFile = get(),
            telemetry = get(),
        )
    }
    viewModel { params ->
        AddDocumentViewModel(
            // Absent when the flow is opened from "Add a document" rather than from a
            // named row; `getOrNull` is what makes one definition serve both.
            prefillType = params.getOrNull<DocumentType>(),
            addDocument = get(),
            activeCar = get(),
            currentOwner = get(),
            telemetry = get(),
        )
    }
    viewModel { params ->
        ShareDocumentViewModel(
            documentId = params.get<DocumentId>(),
            observeDetail = get(),
            telemetry = get(),
        )
    }
    viewModel { params ->
        AddSuccessViewModel(
            documentId = params.get<DocumentId>(),
            observeDetail = get(),
            telemetry = get(),
        )
    }
}
