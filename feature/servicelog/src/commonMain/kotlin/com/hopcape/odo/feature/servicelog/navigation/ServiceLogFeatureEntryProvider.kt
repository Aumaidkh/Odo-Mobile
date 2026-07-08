package com.hopcape.odo.feature.servicelog.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailScreen
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailUiState
import com.hopcape.odo.feature.servicelog.presentation.form.ServiceLogFormScreen
import com.hopcape.odo.feature.servicelog.presentation.form.ServiceLogFormUiState
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListScreen
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListUiState

/**
 * ServiceLog's contribution to the navigation graph: registers the whole
 * [OdoDestination.ServiceLog] sealed group — the list, a single entry's detail, and
 * the add/edit form. All three belong to this one feature, so one provider
 * contributes them (a `registerEntries` block can contribute any number of entries).
 * Collected by the `:app` host (`getAll<FeatureEntryProvider>()`), so no other module
 * references servicelog directly.
 *
 * The keys are typed, so each entry receives its args directly — `key.carId`,
 * `key.logId`, `key.editLogId` — no stringly-typed route parsing.
 */
internal class ServiceLogFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.ServiceLog.List> { key -> ServiceLogListRoute(key, navigationManager) }
        entry<OdoDestination.ServiceLog.Detail> { key -> ServiceLogDetailRoute(key, navigationManager) }
        entry<OdoDestination.ServiceLog.AddEdit> { key -> ServiceLogFormRoute(key, navigationManager) }
    }
}

/**
 * The list route host — the hook between the (Step 3) list ViewModel and navigation.
 * Opening a card jumps to its [OdoDestination.ServiceLog.Detail]; the add affordance
 * opens the [OdoDestination.ServiceLog.AddEdit] form for the same car.
 */
@Composable
internal fun ServiceLogListRoute(
    key: OdoDestination.ServiceLog.List,
    navigationManager: NavigationManager,
) {
    // TODO(step 3): source `state` from a koinViewModel + collectAsStateWithLifecycle.
    ServiceLogListScreen(
        state = ServiceLogListUiState(),
        carId = key.carId,
        onOpenDetail = { logId ->
            navigationManager.navigateTo(OdoDestination.ServiceLog.Detail(logId = logId, carId = key.carId))
        },
        onAddLog = { navigationManager.navigateTo(OdoDestination.ServiceLog.AddEdit(carId = key.carId)) },
        onBack = { navigationManager.back() },
    )
}

/**
 * The detail route host. Editing opens the same [OdoDestination.ServiceLog.AddEdit]
 * form in edit mode ([OdoDestination.ServiceLog.AddEdit.editLogId] = this entry).
 */
@Composable
internal fun ServiceLogDetailRoute(
    key: OdoDestination.ServiceLog.Detail,
    navigationManager: NavigationManager,
) {
    // TODO(step 3): source `state` from a koinViewModel + collectAsStateWithLifecycle.
    ServiceLogDetailScreen(
        state = ServiceLogDetailUiState(),
        logId = key.logId,
        carId = key.carId,
        onEdit = {
            navigationManager.navigateTo(
                OdoDestination.ServiceLog.AddEdit(carId = key.carId, editLogId = key.logId),
            )
        },
        onBack = { navigationManager.back() },
    )
}

/**
 * The add/edit form route host. Both a successful save and back simply pop the form;
 * the underlying list observes the repository, so it reflects the change on return.
 */
@Composable
internal fun ServiceLogFormRoute(
    key: OdoDestination.ServiceLog.AddEdit,
    navigationManager: NavigationManager,
) {
    // TODO(step 3): source `state` from a koinViewModel + collectAsStateWithLifecycle.
    ServiceLogFormScreen(
        state = ServiceLogFormUiState(isEditing = key.editLogId != null),
        carId = key.carId,
        editLogId = key.editLogId,
        onSaved = { navigationManager.back() },
        onBack = { navigationManager.back() },
    )
}
