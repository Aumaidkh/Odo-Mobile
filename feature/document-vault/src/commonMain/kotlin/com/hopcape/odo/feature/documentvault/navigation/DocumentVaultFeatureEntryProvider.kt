package com.hopcape.odo.feature.documentvault.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.feature.documentvault.presentation.vault.DocumentVaultScreen
import com.hopcape.odo.feature.documentvault.presentation.vault.sampleVaultAttention

/**
 * Document vault's contribution to the navigation graph: registers the
 * [OdoDestination.Documents.Vault] overview. Modelled as a group so the per-document
 * detail + add/edit screens slot in here later without touching the shared registry.
 * Collected by the `:app` host via `getAll<FeatureEntryProvider>()`.
 */
internal class DocumentVaultFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Documents.Vault> { DocumentVaultRoute(navigationManager) }
    }
}

/**
 * The vault route host — renders sample documents until the vault ViewModel (backed by
 * the local document store + reminder engine) lands. Add / renew / open are M2 stubs.
 */
@Composable
internal fun DocumentVaultRoute(navigationManager: NavigationManager) {
    DocumentVaultScreen(
        state = sampleVaultAttention(),
        onAdd = { /* TODO(M2): open the add-document form for this type. */ },
        onRenew = { /* TODO(M2): open the renew flow. */ },
        onOpen = { /* TODO(M2): open the document detail. */ },
        onAddDocument = { /* TODO(M2): open the add-document picker. */ },
        onBack = { navigationManager.back() },
    )
}
