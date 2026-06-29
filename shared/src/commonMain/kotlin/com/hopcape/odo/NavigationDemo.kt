package com.hopcape.odo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.LocalNavigationManager
import com.hopcape.odo.core.navigation.Navigator
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo

/*
 * Demo wiring that stands in for the future `:feature:*` modules. Each
 * FeatureEntryProvider below is what a real feature module would expose (and
 * register in its Koin module); the screens drive navigation purely through
 * LocalNavigationManager — in production a feature's ViewModel injects
 * NavigationManager and calls it instead. No screen touches the back stack.
 */

// --- Feature graph contributions (the Hilt-@IntoSet / Koin-getAll analog) ---

class DashboardEntryProvider : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Home> { HomeScreen() }
        entry<OdoDestination.BillScanner> { BillScannerScreen() }
    }
}

class GarageEntryProvider : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Garage> { GarageScreen() }
        entry<OdoDestination.CarDetail> { key -> CarDetailScreen(key.carId) }
        entry<OdoDestination.AddServiceLog> { key -> AddServiceLogScreen(key.carId) }
    }
}

class RemindersEntryProvider : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Reminders> { SimpleScreen("Reminders") }
    }
}

class ProfileEntryProvider : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Profile> { SimpleScreen("Profile") }
    }
}

/** The set the app collects and hands to the host — replace with `getKoin().getAll()`. */
fun demoEntryProviders(): List<FeatureEntryProvider> = listOf(
    DashboardEntryProvider(),
    GarageEntryProvider(),
    RemindersEntryProvider(),
    ProfileEntryProvider(),
)

// --- Screens ---

@Composable
private fun HomeScreen() {
    val nav = LocalNavigationManager.current
    Screen("Home — dashboard") {
        Button(onClick = { nav.navigateTo(OdoDestination.CarDetail("MH-12-AB-1234")) }) {
            Text("Open my car")
        }
        Button(onClick = { nav.navigateTo(OdoDestination.BillScanner) }) {
            Text("Scan a bill")
        }
    }
}

@Composable
private fun GarageScreen() {
    val nav = LocalNavigationManager.current
    Screen("Garage — your cars") {
        Button(onClick = { nav.navigateTo(OdoDestination.CarDetail("DL-3C-AT-9090")) }) {
            Text("Open DL-3C-AT-9090")
        }
    }
}

@Composable
private fun CarDetailScreen(carId: String) {
    val nav = LocalNavigationManager.current
    Screen("Car $carId") {
        Button(onClick = { nav.navigateTo(OdoDestination.AddServiceLog(carId)) }) {
            Text("Add service log")
        }
        Button(onClick = { nav.back() }) { Text("Back") }
    }
}

@Composable
private fun AddServiceLogScreen(carId: String) {
    val nav = LocalNavigationManager.current
    Screen("Add service log for $carId") {
        Button(onClick = { nav.back() }) { Text("Back") }
    }
}

@Composable
private fun BillScannerScreen() {
    val nav = LocalNavigationManager.current
    Screen("Bill Scanner") {
        Button(onClick = { nav.back() }) { Text("Back") }
    }
}

@Composable
private fun SimpleScreen(title: String) = Screen(title) {}

@Composable
private fun Screen(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

// --- Bottom navigation ---

@Composable
fun OdoBottomBar(navigator: Navigator) {
    val nav = LocalNavigationManager.current
    val current = navigator.backStack.lastOrNull()
    NavigationBar {
        OdoDestination.topLevel.forEach { destination ->
            NavigationBarItem(
                selected = current == destination,
                onClick = {
                    // Reselect-safe tab switch: reset to the root, then land on the tab.
                    nav.navigateTo(
                        destination = destination,
                        popUpTo = OdoDestination.Home,
                        singleTop = true,
                    )
                },
                icon = { Text(destination.label.take(1)) },
                label = { Text(destination.label) },
            )
        }
    }
}
