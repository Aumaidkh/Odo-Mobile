package com.hopcape.odo.feature.fairnesscheck.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FairnessLineInput
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.isBillScanFlowStep
import com.hopcape.odo.core.navigation.leaveFlow
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessCheckInput
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessEffect
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessEvent
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessReportScreen
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Fairness-check's contribution to the navigation graph: the reusable
 * [OdoDestination.Fairness] entry. Any feature invokes fairness the same way — build the
 * input, navigate, and the check happens here.
 */
internal class FairnessCheckFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Fairness> { key -> FairnessRoute(key, navigationManager) }
    }
}

@Composable
internal fun FairnessRoute(key: OdoDestination.Fairness, navigationManager: NavigationManager) {
    val input = remember(key) { key.toInput() }
    val viewModel = koinViewModel<FairnessViewModel> { parametersOf(input) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Fires on opening the report and again whenever it is uncovered — only the top entry is
    // composed, so coming back from the profile editor re-enters here while the ViewModel
    // (held by the entry) keeps what it already knows. This is what lets a city set in the
    // meantime turn the "we don't know your city" state into the report the owner asked for.
    LaunchedEffect(viewModel) { viewModel.onEvent(FairnessEvent.Shown) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is FairnessEffect.OpenReportOvercharge ->
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.ReportOvercharge(logId = effect.logId, carId = effect.carId),
                )

            FairnessEffect.OpenEditProfile -> navigationManager.navigateTo(OdoDestination.Profile.Edit)

            // Not a step back: the confirm step for a bill that is already saved sits behind
            // this screen, and so does the viewfinder behind that. The whole errand comes off
            // and the owner lands on whatever opened it.
            FairnessEffect.LeaveFlow -> navigationManager.leaveFlow(::isBillScanFlowStep)
        }
    }

    FairnessReportScreen(state = state, onEvent = viewModel::onEvent)
}

/** Map the primitive nav input to what the ViewModel needs (the domain never sees nav types). */
private fun OdoDestination.Fairness.toInput(): FairnessCheckInput = FairnessCheckInput(
    items = items.map { it.toQueryItem() },
    logId = logId,
    carId = carId,
)

/**
 * An unrecognised category name is dropped rather than guessed. The line still shows what
 * was paid; it simply goes unjudged, which is the honest reading of "we don't know what this
 * job was".
 */
private fun FairnessLineInput.toQueryItem(): FairnessQueryItem = FairnessQueryItem(
    // A blank label is no label: the caller had nothing to call this line, and the screen
    // names it generically rather than drawing an empty row.
    label = label.takeIf { it.isNotBlank() },
    category = category?.let { name -> ServiceCategory.entries.firstOrNull { it.name == name } },
    amount = Amount.of(amountPaise).getOrElse { Amount.ZERO },
)
