package com.hopcape.odo.feature.documentvault.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentScreen
import com.hopcape.odo.feature.documentvault.presentation.add.AddDocumentUiState
import com.hopcape.odo.feature.documentvault.presentation.detail.DocumentDetailScreen
import com.hopcape.odo.feature.documentvault.presentation.detail.sampleDocumentDetail
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultScreen
import com.hopcape.odo.feature.documentvault.presentation.vault.sampleVaultAttention

/**
 * Document vault's contribution to the navigation graph: the [OdoDestination.Documents]
 * group — the [OdoDestination.Documents.Vault] overview and the
 * [OdoDestination.Documents.Add] flow. Modelled as a group so the per-document detail +
 * review-and-confirm screens slot in here later without touching the shared registry.
 * Collected by the `:app` host via `getAll<FeatureEntryProvider>()`.
 */
internal class DocumentVaultFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Documents.Vault> { DocumentVaultRoute(navigationManager) }
        entry<OdoDestination.Documents.Add> { AddDocumentRoute(navigationManager) }
        entry<OdoDestination.Documents.Detail> { DocumentDetailRoute(navigationManager) }
    }
}

/**
 * The vault route host — renders sample documents until the vault ViewModel (backed by
 * the local document store + reminder engine) lands. Adding routes into the add flow;
 * renew / open are M2 stubs.
 */
@Composable
internal fun DocumentVaultRoute(navigationManager: NavigationManager) {
    DocumentVaultScreen(
        state = sampleVaultAttention(),
        onAdd = { navigationManager.navigateTo(OdoDestination.Documents.Add) },
        onRenew = { navigationManager.navigateTo(OdoDestination.Documents.Detail) },
        onOpen = { navigationManager.navigateTo(OdoDestination.Documents.Detail) },
        onAddDocument = { navigationManager.navigateTo(OdoDestination.Documents.Add) },
        onBack = { navigationManager.back() },
    )
}

/**
 * The add-document route host — holds the local type selection until the vault ViewModel
 * lands. Each capture method routes into its review-and-confirm step (M2).
 */
@Composable
internal fun AddDocumentRoute(navigationManager: NavigationManager) {
    var state by remember { mutableStateOf(AddDocumentUiState()) }
    AddDocumentScreen(
        state = state,
        onKindSelect = { state = state.copy(selectedKind = it) },
        onScan = { /* TODO(M2): scan with camera -> review & confirm. */ },
        onFilePicked = { /* uri -> TODO(M2): if non-null, proceed to review & confirm with the file. */ },
        onImportDigiLocker = { /* TODO(M2): DigiLocker import -> review & confirm. */ },
        onClose = { navigationManager.back() },
    )
}

/**
 * The document-detail route host — renders sample Insurance detail until the vault
 * ViewModel lands. View / renew and the file actions (replace / share / download /
 * delete) are M2 stubs.
 */
@Composable
internal fun DocumentDetailRoute(navigationManager: NavigationManager) {
    DocumentDetailScreen(
        state = sampleDocumentDetail(),
        onView = { /* TODO(M2): open the stored file. */ },
        onRenew = { /* TODO(M2): open the renew flow. */ },
        onReplace = { /* TODO(M2): replace the stored file. */ },
        onShare = { /* TODO(M2): share the document. */ },
        onDownload = { /* TODO(M2): download the PDF. */ },
        onDelete = { /* TODO(M2): confirm + delete the document. */ },
        onBack = { navigationManager.back() },
    )
}
