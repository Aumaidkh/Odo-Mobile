package com.hopcape.odo.feature.billcheck.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.share.rememberTextSharer
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisEffect
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisSheetContent
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisViewModel
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckEffect
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckScreen
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The bill check's contribution to the navigation graph: the result screen, plus "How we
 * know" as a real destination rather than a boolean inside the screen.
 */
internal class BillCheckFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.BillCheck.Result> { key -> ResultRoute(key, navigationManager) }
        entry<OdoDestination.BillCheck.Basis>(
            metadata = ModalBottomSheetSceneStrategy.bottomSheet(),
        ) { key -> BasisRoute(key, navigationManager) }
    }
}

@Composable
private fun ResultRoute(
    key: OdoDestination.BillCheck.Result,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<BillCheckViewModel> { parametersOf(key.billId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val share = rememberTextSharer()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            BillCheckEffect.NavigateBack -> navigationManager.back()

            is BillCheckEffect.Share -> share(effect.text)

            is BillCheckEffect.OpenBasis -> navigationManager.navigateTo(
                OdoDestination.BillCheck.Basis(billId = key.billId, lineName = effect.lineName),
            )

            // The bill-check framing, not the general one: someone who has just run out of
            // checks is not shopping for a PDF.
            BillCheckEffect.OpenOffers -> navigationManager.navigateTo(
                OdoDestination.Paywall.OneTimeOffers(context = OFFERS_BILL_CHECK),
            )

            // The scanner rather than the manual form: the nudge asks for the *last* bill,
            // and a photo of it is both faster and worth more to the check than typing.
            BillCheckEffect.AddLastBill -> navigationManager.navigateTo(
                OdoDestination.BillScanner.Capture(),
            )
        }
    }

    BillCheckScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun BasisRoute(
    key: OdoDestination.BillCheck.Basis,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<BasisViewModel> { parametersOf(key.billId, key.lineName) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // The existing "something is wrong" form, closing the sheet on the way — the
            // owner should not be left with a report form under the sheet explaining the
            // number they are reporting. A form of its own is a later slice; Play needs the
            // route to exist, not a bespoke one.
            BasisEffect.ReportPrice -> {
                navigationManager.back()
                navigationManager.navigateTo(OdoDestination.Support.ReportProblem)
            }
        }
    }

    BasisSheetContent(state = state, onEvent = viewModel::onEvent)
}

/** Matches `OneTimeContext.BILL_CHECK`, which the key carries as a primitive. */
private const val OFFERS_BILL_CHECK = "BILL_CHECK"
