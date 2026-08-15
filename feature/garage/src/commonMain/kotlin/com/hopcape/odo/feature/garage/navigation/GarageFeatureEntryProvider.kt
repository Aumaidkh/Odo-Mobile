package com.hopcape.odo.feature.garage.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.pdf.rememberHtmlToPdf
import com.hopcape.odo.core.platform.share.ShareMimeType
import com.hopcape.odo.core.platform.share.rememberFileSharer
import com.hopcape.odo.feature.garage.presentation.AddCarEffect
import com.hopcape.odo.feature.garage.presentation.AddCarScreen
import com.hopcape.odo.feature.garage.presentation.AddCarViewModel
import com.hopcape.odo.feature.garage.presentation.EditCarEffect
import com.hopcape.odo.feature.garage.presentation.EditCarScreen
import com.hopcape.odo.feature.garage.presentation.EditCarViewModel
import com.hopcape.odo.feature.garage.presentation.GarageEffect
import com.hopcape.odo.feature.garage.presentation.GarageScreen
import com.hopcape.odo.feature.garage.presentation.GarageViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.AddToHistorySheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsEffect
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.ExportEffect
import com.hopcape.odo.feature.garage.presentation.sheets.ExportEvent
import com.hopcape.odo.feature.garage.presentation.sheets.ExportSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.ExportViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarEffect
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerEffect
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerSheetContent
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
) : FeatureEntryProvider {

    private val nm get() = navigationManager

    /** Replace the sheet [from] with [to] — one command, no lingering sheet on back. */
    private fun replace(from: OdoDestination, to: OdoDestination) =
        nm.navigateTo(to, popUpTo = from, inclusive = true)

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Garage.Home> { GarageRoute(nm) }

        // --- Sheets ---
        val sheet = ModalBottomSheetSceneStrategy.bottomSheet()

        entry<OdoDestination.Garage.CarActions>(metadata = sheet) { CarActionsRoute(::replace) }
        entry<OdoDestination.Garage.UpdateOdometer>(metadata = sheet) { UpdateOdometerRoute(nm) }
        entry<OdoDestination.Garage.Export>(metadata = sheet) { ExportRoute() }
        entry<OdoDestination.Garage.RemoveCar>(metadata = sheet) { RemoveCarRoute(nm, ::replace) }

        entry<OdoDestination.Garage.AddToHistory>(metadata = sheet) { AddToHistoryRoute(::replace) }

        // --- Full screens ---
        entry<OdoDestination.Garage.EditCar> { EditCarRoute(nm) }
        entry<OdoDestination.Garage.AddCar> { AddCarRoute(nm) }
    }
}

/**
 * The "add to service history" sheet — a menu, so it has no ViewModel of its own.
 *
 * Two of its rows open a per-car service-log screen, so it resolves the active car itself.
 * A `null` car means those rows do nothing: refusing to navigate beats inventing an id,
 * because opening someone else's car is worse than opening none.
 */
@Composable
private fun AddToHistoryRoute(replace: (OdoDestination, OdoDestination) -> Unit) {
    val here = OdoDestination.Garage.AddToHistory
    val activeCar = koinInject<ActiveCarProvider>()
    val carId by activeCar.activeCarId.collectAsStateWithLifecycle()

    AddToHistorySheetContent(
        onScan = { replace(here, OdoDestination.BillScanner.Capture()) },
        onManual = { carId?.let { replace(here, OdoDestination.ServiceLog.AddEdit(carId = it.value)) } },
        onAddDocument = { replace(here, OdoDestination.Documents.Add()) },
        onViewAll = { carId?.let { replace(here, OdoDestination.ServiceLog.List(carId = it.value)) } },
    )
}

@Composable
internal fun GarageRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<GarageViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            GarageEffect.OpenAddCar -> navigationManager.navigateTo(OdoDestination.Garage.AddCar)
            GarageEffect.OpenUpdateOdometer -> navigationManager.navigateTo(OdoDestination.Garage.UpdateOdometer)
            GarageEffect.OpenCarActions -> navigationManager.navigateTo(OdoDestination.Garage.CarActions)
            GarageEffect.OpenDocumentVault -> navigationManager.navigateTo(OdoDestination.Documents.Vault)
            GarageEffect.OpenAddDocument -> navigationManager.navigateTo(OdoDestination.Documents.Add())
            GarageEffect.OpenAddToHistory -> navigationManager.navigateTo(OdoDestination.Garage.AddToHistory)
            is GarageEffect.OpenDocument ->
                navigationManager.navigateTo(OdoDestination.Documents.Detail(documentId = effect.id.value))

            is GarageEffect.OpenService -> navigationManager.navigateTo(
                OdoDestination.ServiceLog.Detail(logId = effect.logId.value, carId = effect.carId),
            )

            GarageEffect.OpenAutoOdometerEducation -> navigationManager.navigateTo(
                OdoDestination.AutoOdometer.Education(
                    mode = OdoDestination.AutoOdometer.AutoOdometerFlowMode.STEREO,
                ),
            )

            GarageEffect.OpenAutoOdometerSettings ->
                navigationManager.navigateTo(OdoDestination.AutoOdometer.Settings)
        }
    }

    GarageScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun UpdateOdometerRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<UpdateOdometerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            UpdateOdometerEffect.Saved -> navigationManager.back()
        }
    }

    UpdateOdometerSheetContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun CarActionsRoute(replace: (OdoDestination, OdoDestination) -> Unit) {
    val viewModel = koinViewModel<CarActionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val here = OdoDestination.Garage.CarActions

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            CarActionsEffect.OpenEdit -> replace(here, OdoDestination.Garage.EditCar)
            CarActionsEffect.OpenExport -> replace(here, OdoDestination.Garage.Export)
            CarActionsEffect.OpenRemove -> replace(here, OdoDestination.Garage.RemoveCar)
        }
    }

    CarActionsSheetContent(state = state, onEvent = viewModel::onEvent)
}

/**
 * The export sheet's host. Both effects are platform work the ViewModel cannot do: laying
 * a document out needs a renderer with a UI host, and presenting a share sheet needs
 * whatever is hosting the UI. The ViewModel builds the document and decides where it goes;
 * this runs it — the same bridge the service log's share sheet uses.
 */
@Composable
private fun ExportRoute() {
    val viewModel = koinViewModel<ExportViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val htmlToPdf = rememberHtmlToPdf()
    val shareFile = rememberFileSharer()
    // Rendering suspends and CollectEffects hands over a plain lambda, so the render runs
    // on the composition's own scope. It is cancelled with the sheet, which is what should
    // happen to a document nobody is waiting for any more.
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ExportEffect.RenderDocument -> scope.launch {
                viewModel.onEvent(
                    ExportEvent.Rendered(
                        bytes = htmlToPdf(effect.html, effect.documentName),
                        via = effect.via,
                    ),
                )
            }

            is ExportEffect.ShareFile ->
                shareFile(effect.storageKey, ShareMimeType.PDF, effect.title)
        }
    }

    ExportSheetContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun RemoveCarRoute(
    navigationManager: NavigationManager,
    replace: (OdoDestination, OdoDestination) -> Unit,
) {
    val viewModel = koinViewModel<RemoveCarViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            RemoveCarEffect.OpenExport ->
                replace(OdoDestination.Garage.RemoveCar, OdoDestination.Garage.Export)

            // The garage falls back to its "add your car" state on its own once the car is
            // gone, so closing the sheet is all that is left to do.
            RemoveCarEffect.Removed, RemoveCarEffect.Dismiss -> navigationManager.back()
        }
    }

    RemoveCarSheetContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun EditCarRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<EditCarViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            EditCarEffect.Saved, EditCarEffect.NavigateBack -> navigationManager.back()
        }
    }

    EditCarScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun AddCarRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<AddCarViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            AddCarEffect.Added, AddCarEffect.NavigateBack -> navigationManager.back()
        }
    }

    AddCarScreen(state = state, onEvent = viewModel::onEvent)
}
