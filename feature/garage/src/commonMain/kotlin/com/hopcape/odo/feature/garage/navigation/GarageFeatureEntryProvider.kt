package com.hopcape.odo.feature.garage.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import org.koin.compose.koinInject
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.garage.presentation.AddCarScreen
import com.hopcape.odo.feature.garage.presentation.EditCarScreen
import com.hopcape.odo.feature.garage.presentation.GarageScreen
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.presentation.sampleGarage
import com.hopcape.odo.feature.garage.presentation.sheets.AddToHistorySheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.ExportSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.SwitchCarSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerSheetContent

/**
 * Garage's contribution to the navigation graph — the [OdoDestination.Garage] group:
 * the tab root, its bottom-sheet destinations (tagged so
 * [ModalBottomSheetSceneStrategy] renders them as sheets), and the full-screen editors.
 * As an aggregator, Garage never imports a sibling feature — it jumps to
 * ServiceLog / Documents / BillScanner through the shared [OdoDestination] keys.
 *
 * Navigating **from** a sheet onward replaces it (`popUpTo` the sheet, `inclusive`),
 * so back from the target lands on the garage, not a re-shown sheet.
 */
internal class GarageFeatureEntryProvider(
    private val navigationManager: NavigationManager,
    private val activeCar: ActiveCarProvider,
) : FeatureEntryProvider {

    private val nm get() = navigationManager

    /**
     * The car every jump into a per-car destination is about, read at tap time. `null` until
     * setup has stored a car, which is why each use below refuses to navigate rather than
     * inventing an id — opening someone else's car is worse than not opening one.
     */
    private val carId: CarId? get() = activeCar.activeCarId.value

    /** Run [action] with the active car's id, or do nothing when there isn't one yet. */
    private inline fun withCar(action: (String) -> Unit) = carId?.let { action(it.value) }

    /** Replace the sheet [from] with [to] — one command, no lingering sheet on back. */
    private fun replace(from: OdoDestination, to: OdoDestination) =
        nm.navigateTo(to, popUpTo = from, inclusive = true)

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Garage.Home> { GarageRoute(nm) }

        // --- Sheets ---
        val sheet = ModalBottomSheetSceneStrategy.bottomSheet()

        entry<OdoDestination.Garage.CarActions>(metadata = sheet) {
            CarActionsSheetContent(
                onEditCar = { replace(OdoDestination.Garage.CarActions, OdoDestination.Garage.EditCar) },
                onSwitchCar = { replace(OdoDestination.Garage.CarActions, OdoDestination.Garage.SwitchCar) },
                onAddCar = { replace(OdoDestination.Garage.CarActions, OdoDestination.Garage.AddCar) },
                onExport = { replace(OdoDestination.Garage.CarActions, OdoDestination.Garage.Export) },
                onRemoveCar = { replace(OdoDestination.Garage.CarActions, OdoDestination.Garage.RemoveCar) },
            )
        }

        entry<OdoDestination.Garage.UpdateOdometer>(metadata = sheet) {
            UpdateOdometerSheetContent(onSave = { nm.back() })
        }

        entry<OdoDestination.Garage.AddToHistory>(metadata = sheet) {
            AddToHistorySheetContent(
                onScan = { replace(OdoDestination.Garage.AddToHistory, OdoDestination.BillScanner.Capture) },
                onManual = { withCar { replace(OdoDestination.Garage.AddToHistory, OdoDestination.ServiceLog.AddEdit(carId = it)) } },
                onAddDocument = { replace(OdoDestination.Garage.AddToHistory, OdoDestination.Documents.Add) },
                onViewAll = { withCar { replace(OdoDestination.Garage.AddToHistory, OdoDestination.ServiceLog.List(carId = it)) } },
            )
        }

        entry<OdoDestination.Garage.SwitchCar>(metadata = sheet) {
            SwitchCarSheetContent(
                onSelect = { nm.back() }, // sample: selecting the active car just closes the sheet
                onAddCar = { replace(OdoDestination.Garage.SwitchCar, OdoDestination.Garage.AddCar) },
            )
        }

        entry<OdoDestination.Garage.Export>(metadata = sheet) {
            ExportSheetContent(
                onPdf = { nm.back() }, // TODO: render + save the PDF
                onShare = { nm.back() }, // TODO: open the share sheet
            )
        }

        entry<OdoDestination.Garage.RemoveCar>(metadata = sheet) {
            RemoveCarSheetContent(
                onExportFirst = { replace(OdoDestination.Garage.RemoveCar, OdoDestination.Garage.Export) },
                onRemove = { nm.back() }, // TODO: delete the car + fall back to the empty garage
                onCancel = { nm.back() },
            )
        }

        // --- Full screens ---
        entry<OdoDestination.Garage.EditCar> {
            EditCarScreen(onClose = { nm.back() }, onSave = { nm.back() })
        }
        entry<OdoDestination.Garage.AddCar> {
            AddCarScreen(onClose = { nm.back() }, onAdd = { nm.back() })
        }
    }
}

/**
 * The Garage tab route host. Renders sample state until the aggregation use case
 * (reading the `:core:domain` car / document / service-log ports) lands; swap
 * `sampleGarage()` ↔ `sampleEmptyGarage()` to preview the empty vs populated state.
 */
@Composable
internal fun GarageRoute(navigationManager: NavigationManager) {
    val activeCar = koinInject<ActiveCarProvider>()
    var filter by remember { mutableStateOf(ServiceFacet.ALL) }
    GarageScreen(
        state = sampleGarage(filter),
        onAddCar = { navigationManager.navigateTo(OdoDestination.Garage.AddCar) },
        onUpdateOdometer = { navigationManager.navigateTo(OdoDestination.Garage.UpdateOdometer) },
        onCarMenu = { navigationManager.navigateTo(OdoDestination.Garage.CarActions) },
        onManageDocuments = { navigationManager.navigateTo(OdoDestination.Documents.Vault) },
        onOpenDocument = { navigationManager.navigateTo(OdoDestination.Documents.Detail) },
        onAddDocument = { navigationManager.navigateTo(OdoDestination.Documents.Add) },
        onAddService = { navigationManager.navigateTo(OdoDestination.Garage.AddToHistory) },
        onOpenService = { logId ->
            activeCar.activeCarId.value?.let { carId ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.Detail(logId = logId.value, carId = carId.value))
            }
        },
        onFilterChange = { filter = it },
    )
}
