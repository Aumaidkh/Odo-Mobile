package com.hopcape.odo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.OdoNavHost
import com.hopcape.odo.core.navigation.rememberNavigator
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * The app's composition root. Koin is already started by the platform bootstrap
 * (Android `OdoApplication` / iOS `MainViewController`), and Koin 4.x exposes that
 * global graph to the composition automatically — so feature routes can
 * `koinViewModel()` / `koinInject()` without any wrapper here.
 *
 * The nav host is driven entirely from DI: the shared [NavigationManager] and every
 * feature's [FeatureEntryProvider] (`getAll` — the multibinding analog). First run
 * lands on the [OdoDestination.Welcome] intro carousel, which routes into
 * [OdoDestination.Onboarding] (car setup); completing that routes to [OdoDestination.Home].
 */
@Composable
fun App() {
    OdoTheme {
        val navigationManager = koinInject<NavigationManager>()
        val navigator = rememberNavigator(OdoDestination.ServiceLog.List("aaa"))

        // Every wired feature contributes its screens; the placeholder still
        // serves Home (where onboarding lands) until that feature ships.
        val featureProviders = getKoin().getAll<FeatureEntryProvider>()
        val entryProviders = remember(featureProviders) {
            featureProviders + PlaceholderHomeEntryProvider
        }

        OdoNavHost(
            navigator = navigator,
            navigationManager = navigationManager,
            entryProviders = entryProviders,
        )
    }
}

/**
 * Stand-in for the Home route until its feature lands. Onboarding routes here on
 * completion (see `StartDestinationMapping`), so the graph must always resolve it.
 */
private val PlaceholderHomeEntryProvider = object : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Home> {
            OdoScreen(title = "Odo") { padding ->
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "your car's AI best friend",
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
        }
    }
}
