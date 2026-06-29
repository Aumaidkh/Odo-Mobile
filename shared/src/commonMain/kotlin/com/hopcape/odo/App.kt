package com.hopcape.odo

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hopcape.odo.core.navigation.LocalNavigationManager
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.OdoNavHost
import com.hopcape.odo.core.navigation.rememberNavigator

@Composable
@Preview
fun App() {
    MaterialTheme {
        // In production these come from Koin: `koinInject<NavigationManager>()` and
        // `getKoin().getAll<FeatureEntryProvider>()`. Held in `remember` here so the
        // demo runs without bootstrapping Koin.
        val navigationManager = remember { NavigationManager() }
        val navigator = rememberNavigator(OdoDestination.Home)
        val entryProviders = remember { demoEntryProviders() }

        CompositionLocalProvider(
            LocalNavigationManager provides navigationManager,
        ) {
            Scaffold(
                bottomBar = { OdoBottomBar(navigator) },
            ) { innerPadding ->
                OdoNavHost(
                    navigator = navigator,
                    navigationManager = navigationManager,
                    entryProviders = entryProviders,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
