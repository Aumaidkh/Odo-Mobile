package com.hopcape.odo.feature.servicelog.navigation

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
import com.hopcape.odo.feature.servicelog.presentation.list.model.ServiceLogDirection
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailScreen
import com.hopcape.odo.feature.servicelog.presentation.detail.sampleDetailState
import com.hopcape.odo.feature.servicelog.presentation.form.DistanceUnit
import com.hopcape.odo.feature.servicelog.presentation.form.ServiceLogFormScreen
import com.hopcape.odo.feature.servicelog.presentation.form.sampleFormState
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogFilter
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListScreen
import com.hopcape.odo.feature.servicelog.presentation.list.sampleServiceLogListState

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
    // TODO(step 3+): replace the sample state + local direction/filter with a
    //  koinViewModel once the list ViewModel lands. The direction toggle is a
    //  temporary affordance to compare the two mockups live.
    var direction by remember { mutableStateOf(ServiceLogDirection.LEDGER) }
    var filter by remember { mutableStateOf(ServiceLogFilter.ALL) }
    ServiceLogListScreen(
        state = sampleServiceLogListState(filter),
        direction = direction,
        onDirectionChange = { direction = it },
        onOpenDetail = { logId ->
            navigationManager.navigateTo(OdoDestination.ServiceLog.Detail(logId = logId, carId = key.carId))
        },
        onAddLog = { navigationManager.navigateTo(OdoDestination.ServiceLog.AddEdit(carId = key.carId)) },
        onScanBill = { /* TODO(M2): navigate to BillScanner once that feature ships. */ },
        onShareRecord = { /* TODO(passport): share the resale record (Phase 2). */ },
        onOpenFilters = { /* TODO: advanced filters sheet. */ },
        onFilterChange = { filter = it },
        onBack = { navigationManager.back() },
    )
}

/**
 * The detail route host — the combined fairness + resale-proof view for one entry.
 */
@Composable
internal fun ServiceLogDetailRoute(
    key: OdoDestination.ServiceLog.Detail,
    navigationManager: NavigationManager,
) {
    // TODO(step 3+): source `state` from a koinViewModel keyed by key.logId.
    ServiceLogDetailScreen(
        state = sampleDetailState(),
        onShare = { /* TODO(passport): share the verified record (Phase 2). */ },
        onReportOvercharge = { /* TODO: ReportOverchargeUseCase. */ },
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
    // TODO(step 3+): source `state` from a koinViewModel keyed by key.editLogId; this
    //  local reducer is a stand-in so the form is interactive until then.
    var form by remember { mutableStateOf(sampleFormState(isEditing = key.editLogId != null)) }
    ServiceLogFormScreen(
        state = form,
        onWorkshopChange = { form = form.copy(workshop = form.workshop.update(it)) },
        onDateChange = { form = form.copy(date = form.date.update(it)) },
        onOdometerChange = { form = form.copy(odometer = form.odometer.update(it)) },
        onOdometerUnitToggle = {
            form = form.copy(odometerUnit = if (form.odometerUnit == DistanceUnit.KM) DistanceUnit.MILES else DistanceUnit.KM)
        },
        onAmountChange = { form = form.copy(amount = form.amount.update(it)) },
        onCategoryToggle = { category ->
            val next = if (category in form.categories) form.categories - category else form.categories + category
            form = form.copy(categories = next)
        },
        onNotesChange = { form = form.copy(notes = form.notes.update(it)) },
        onScanBill = { /* TODO(M2): navigate to BillScanner once that feature ships. */ },
        onAttachBill = { /* TODO(M2): attach a bill photo. */ },
        onSave = { navigationManager.back() },
        onClose = { navigationManager.back() },
    )
}
