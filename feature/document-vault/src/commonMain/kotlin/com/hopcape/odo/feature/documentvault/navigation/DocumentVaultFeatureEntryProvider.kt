package com.hopcape.odo.feature.documentvault.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentEffect
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentEvent
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentScreen
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentViewModel
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailEffect
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailEvent
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailScreen
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailViewModel
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentEffect
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentEvent
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentSheetContent
import com.hopcape.odo.feature.documentvault.presentation.share.ShareDocumentViewModel
import com.hopcape.odo.feature.documentvault.presentation.success.AddSuccessScreen
import com.hopcape.odo.feature.documentvault.presentation.success.AddSuccessViewModel
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultEffect
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultEvent
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultScreen
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Document vault's contribution to the navigation graph: the [OdoDestination.Documents]
 * group — the vault overview, the add flow, a document's detail, the add confirmation and
 * the share sheet. Collected by the `:app` host via `getAll<FeatureEntryProvider>()`.
 */
internal class DocumentVaultFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Documents.Vault> { DocumentVaultRoute(navigationManager) }
        entry<OdoDestination.Documents.Add> { key -> AddDocumentRoute(navigationManager, key) }
        entry<OdoDestination.Documents.Detail> { key -> DocumentDetailRoute(navigationManager, key) }
        entry<OdoDestination.Documents.AddSuccess> { key -> AddSuccessRoute(navigationManager, key) }
        entry<OdoDestination.Documents.Share>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) { key ->
            ShareDocumentRoute(key)
        }
    }
}

/** Exit an add/detail sub-flow and reset to the vault overview with a clean back stack. */
private fun NavigationManager.backToDocuments() {
    val vault = OdoDestination.Documents.Vault
    navigateTo(vault, popUpTo = vault, inclusive = true)
}

/**
 * The type a navigation key names, or null if it names none or one this build does not know.
 *
 * Keys carry the enum's name as a string because a key is serialized into the back stack;
 * an unrecognised name opens the add flow with nothing preselected rather than failing.
 */
internal fun String?.toDocumentType(): DocumentType? =
    this?.let { name -> DocumentType.entries.firstOrNull { it.name == name } }

@Composable
internal fun DocumentVaultRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<DocumentVaultViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is DocumentVaultEffect.OpenDocument ->
                navigationManager.navigateTo(OdoDestination.Documents.Detail(documentId = effect.id.value))

            is DocumentVaultEffect.OpenAdd ->
                navigationManager.navigateTo(OdoDestination.Documents.Add(prefillType = effect.prefillType?.name))

            DocumentVaultEffect.NavigateBack -> navigationManager.back()
        }
    }

    DocumentVaultScreen(
        state = state,
        onAdd = { viewModel.onEvent(DocumentVaultEvent.AddTapped(it)) },
        onRenew = { viewModel.onEvent(DocumentVaultEvent.DocumentTapped(it)) },
        onOpen = { viewModel.onEvent(DocumentVaultEvent.DocumentTapped(it)) },
        onAddDocument = { viewModel.onEvent(DocumentVaultEvent.AddAnyTapped) },
        onBack = { viewModel.onEvent(DocumentVaultEvent.BackTapped) },
    )
}

@Composable
internal fun AddDocumentRoute(navigationManager: NavigationManager, key: OdoDestination.Documents.Add) {
    val viewModel = koinViewModel<AddDocumentViewModel> {
        parametersOf(key.prefillType.toDocumentType())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is AddDocumentEffect.OpenSuccess ->
                navigationManager.navigateTo(OdoDestination.Documents.AddSuccess(documentId = effect.id.value))

            // The scanner files the document itself, so this leaves the add screen behind:
            // coming back to it after a paper was already filed would offer to file it twice.
            is AddDocumentEffect.OpenScanner -> navigationManager.navigateTo(
                OdoDestination.BillScanner.Capture(
                    target = ScanTarget.Document,
                    documentType = effect.type.name,
                ),
                popUpTo = key,
                inclusive = true,
            )

            AddDocumentEffect.NavigateBack -> navigationManager.back()
        }
    }

    AddDocumentScreen(
        state = state,
        onTypeSelect = { viewModel.onEvent(AddDocumentEvent.TypeSelected(it)) },
        onScan = { viewModel.onEvent(AddDocumentEvent.Capture.Scan) },
        onFilePicked = { viewModel.onEvent(AddDocumentEvent.Capture.FilePicked(it)) },
        onImportDigiLocker = { viewModel.onEvent(AddDocumentEvent.Capture.DigiLocker) },
        onClose = { viewModel.onEvent(AddDocumentEvent.CloseTapped) },
    )
}

@Composable
internal fun DocumentDetailRoute(navigationManager: NavigationManager, key: OdoDestination.Documents.Detail) {
    val viewModel = koinViewModel<DocumentDetailViewModel> {
        parametersOf(DocumentId(key.documentId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is DocumentDetailEffect.OpenShare ->
                navigationManager.navigateTo(OdoDestination.Documents.Share(documentId = effect.id.value))

            is DocumentDetailEffect.OpenAdd ->
                navigationManager.navigateTo(OdoDestination.Documents.Add(prefillType = effect.prefillType.name))

            // TODO(files): hand the stored file to a platform viewer / the downloads folder.
            is DocumentDetailEffect.OpenFile -> Unit
            is DocumentDetailEffect.DownloadFile -> Unit

            DocumentDetailEffect.NavigateBack -> navigationManager.back()
        }
    }

    DocumentDetailScreen(
        state = state,
        onView = { viewModel.onEvent(DocumentDetailEvent.File.View) },
        onRenew = { viewModel.onEvent(DocumentDetailEvent.Open.Renew) },
        // TODO(files): open the picker, then send File.Replace with what it returns.
        onReplace = { },
        onShare = { viewModel.onEvent(DocumentDetailEvent.Open.Share) },
        onDownload = { viewModel.onEvent(DocumentDetailEvent.File.Download) },
        onDelete = { viewModel.onEvent(DocumentDetailEvent.File.Delete) },
        onBack = { viewModel.onEvent(DocumentDetailEvent.Open.Back) },
    )
}

@Composable
internal fun AddSuccessRoute(navigationManager: NavigationManager, key: OdoDestination.Documents.AddSuccess) {
    val viewModel = koinViewModel<AddSuccessViewModel> {
        parametersOf(DocumentId(key.documentId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    state?.let { success ->
        AddSuccessScreen(
            state = success,
            onBackToDocuments = { navigationManager.backToDocuments() },
            onAddAnother = { navigationManager.navigateTo(OdoDestination.Documents.Add()) },
        )
    }
}

@Composable
internal fun ShareDocumentRoute(key: OdoDestination.Documents.Share) {
    val viewModel = koinViewModel<ShareDocumentViewModel> {
        parametersOf(DocumentId(key.documentId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // TODO(files): hand the stored file to the platform share sheet / downloads.
            is ShareDocumentEffect.ShareFile -> Unit
            is ShareDocumentEffect.DownloadFile -> Unit
        }
    }

    state?.let { share ->
        ShareDocumentSheetContent(
            state = share,
            onShareVia = { viewModel.onEvent(ShareDocumentEvent.ShareVia(it)) },
            onDownload = { viewModel.onEvent(ShareDocumentEvent.DownloadTapped) },
        )
    }
}
