package com.hopcape.odo.feature.fairnesscheck.navigation

import androidx.compose.runtime.Composable
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
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessCheckInput
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessEffect
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

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is FairnessEffect.OpenReportOvercharge ->
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.ReportOvercharge(logId = effect.logId, carId = effect.carId),
                )

            FairnessEffect.OpenProfile -> navigationManager.navigateTo(OdoDestination.Profile.Root)
            FairnessEffect.NavigateBack -> navigationManager.back()
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
