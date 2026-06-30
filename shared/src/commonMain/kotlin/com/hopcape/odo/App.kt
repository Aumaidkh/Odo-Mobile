package com.hopcape.odo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.OdoNavHost
import com.hopcape.odo.core.navigation.rememberNavigator

@Composable
@Preview
fun App() {
    OdoTheme {
        // In production these come from Koin: `koinInject<NavigationManager>()` and
        // `getKoin().getAll<FeatureEntryProvider>()`. Held in `remember` here until
        // the first `:feature:*` modules register their own entry providers.
        val navigationManager = remember { NavigationManager() }
        val navigator = rememberNavigator(OdoDestination.Home)
        val entryProviders = remember { listOf(PlaceholderEntryProvider) }

        OdoNavHost(
            navigator = navigator,
            navigationManager = navigationManager,
            entryProviders = entryProviders,
        )
    }
}

/**
 * Stand-in graph until the real features land. Renders the Home route so the host
 * has something to display; the first `:feature:*` module replaces this.
 */
private val PlaceholderEntryProvider = object : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Home> {
            Box(
                Modifier.fillMaxSize().background(OdoTheme.colors.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Odo",
                    style = OdoTheme.typography.title,
                    color = OdoTheme.colors.accent,
                )
            }
        }
    }
}
