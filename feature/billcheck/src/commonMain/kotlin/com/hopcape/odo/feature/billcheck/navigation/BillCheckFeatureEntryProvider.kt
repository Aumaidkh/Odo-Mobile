package com.hopcape.odo.feature.billcheck.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.share.WHATSAPP_PACKAGE
import com.hopcape.odo.core.platform.share.rememberImageSharer
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisEffect
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisSheetContent
import com.hopcape.odo.feature.billcheck.presentation.howweknow.BasisViewModel
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckEffect
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckEvent
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckScreen
import com.hopcape.odo.feature.billcheck.presentation.result.BillCheckViewModel
import com.hopcape.odo.feature.billcheck.presentation.share.ShareCardEffect
import com.hopcape.odo.feature.billcheck.presentation.share.ShareCardScreen
import com.hopcape.odo.feature.billcheck.presentation.share.ShareCardViewModel
import com.hopcape.odo.feature.billcheck.resources.Res
import com.hopcape.odo.feature.billcheck.resources.bc_headline
import com.hopcape.odo.feature.billcheck.resources.bc_share_failed
import com.hopcape.odo.feature.billcheck.resources.bc_share_saved
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch
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
        entry<OdoDestination.BillCheck.Share> { key -> ShareRoute(key, navigationManager) }
    }
}

@Composable
private fun ResultRoute(
    key: OdoDestination.BillCheck.Result,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<BillCheckViewModel> { parametersOf(key.billId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            BillCheckEffect.NavigateBack -> navigationManager.back()

            // The figures, not the bill id. The card is built from what this screen showed,
            // so the two cannot disagree — and the plate and the workshop never travel.
            BillCheckEffect.OpenShareCard -> state.check?.let { check ->
                navigationManager.navigateTo(
                    OdoDestination.BillCheck.Share(
                        amountPaise = check.worthAsking.paise,
                        flagged = check.flagged.size,
                        lines = check.lineCount,
                    ),
                )
            }

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

    // The offers sheet opens over this screen, so a check bought on it leaves the findings
    // behind it masked until something asks again. Resuming is that ask.
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(BillCheckEvent.Resumed)
        onPauseOrDispose {}
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

/**
 * The card, and the two things an owner does with it.
 *
 * The caption is assembled here rather than in the ViewModel because it is the result
 * screen's own headline, read from the same figures the card is drawn from — one sentence,
 * one number, whichever way it leaves the app.
 */
@Composable
private fun ShareRoute(
    key: OdoDestination.BillCheck.Share,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<ShareCardViewModel> {
        parametersOf(key.amountPaise, key.flagged, key.lines)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shareImage = rememberImageSharer()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val caption = stringResource(Res.string.bc_headline, state.amount.formatRupees())
    val savedMessage = stringResource(Res.string.bc_share_saved)
    val failedMessage = stringResource(Res.string.bc_share_failed)

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ShareCardEffect.NavigateBack -> navigationManager.back()

            // WhatsApp by name, with the chooser behind it for a phone that has none.
            is ShareCardEffect.ShareImage ->
                shareImage(effect.storageKey, caption, WHATSAPP_PACKAGE)

            ShareCardEffect.Saved -> scope.launch { snackbarHostState.showSnackbar(savedMessage) }

            ShareCardEffect.Failed -> scope.launch { snackbarHostState.showSnackbar(failedMessage) }
        }
    }

    ShareCardScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

/** Matches `OneTimeContext.BILL_CHECK`, which the key carries as a primitive. */
private const val OFFERS_BILL_CHECK = "BILL_CHECK"
