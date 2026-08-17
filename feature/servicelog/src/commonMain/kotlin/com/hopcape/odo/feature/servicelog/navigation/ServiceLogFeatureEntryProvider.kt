package com.hopcape.odo.feature.servicelog.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.FairnessLineInput
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.file.rememberFilePicker
import com.hopcape.odo.core.platform.pdf.rememberHtmlToPdf
import com.hopcape.odo.core.platform.share.ShareMimeType
import com.hopcape.odo.core.platform.share.rememberFileSharer
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_detail_bill_attached
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailEffect
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailEvent
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailScreen
import com.hopcape.odo.feature.servicelog.presentation.detail.ServiceLogDetailViewModel
import com.hopcape.odo.feature.servicelog.presentation.form.ServiceLogFormEffect
import com.hopcape.odo.feature.servicelog.presentation.form.ServiceLogFormScreen
import com.hopcape.odo.feature.servicelog.presentation.form.ServiceLogFormViewModel
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListEffect
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListScreen
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListViewModel
import com.hopcape.odo.feature.servicelog.presentation.report.ReportOverchargeEffect
import com.hopcape.odo.feature.servicelog.presentation.report.ReportOverchargeScreen
import com.hopcape.odo.feature.servicelog.presentation.report.ReportOverchargeViewModel
import com.hopcape.odo.feature.servicelog.presentation.share.ShareRecordEffect
import com.hopcape.odo.feature.servicelog.presentation.share.ShareRecordEvent
import com.hopcape.odo.feature.servicelog.presentation.share.ShareRecordSheetContent
import com.hopcape.odo.feature.servicelog.presentation.share.ShareRecordViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

/**
 * ServiceLog's contribution to the navigation graph: registers the whole
 * [OdoDestination.ServiceLog] sealed group — the list, a single entry's detail, the
 * add/edit form, the overcharge report and the share sheet. All five belong to this one
 * feature, so one provider contributes them (a `registerEntries` block can contribute any
 * number of entries). Collected by the `:app` host (`getAll<FeatureEntryProvider>()`), so
 * no other module references servicelog directly.
 *
 * The keys are typed, so each entry receives its args directly — `key.carId`, `key.logId`,
 * `key.editLogId` — no stringly-typed route parsing.
 *
 * The routes below are **only** a state-and-effects bridge: they render a ViewModel's
 * state, forward its events, and translate its effects into navigation commands. Every
 * decision about *what* should happen is made in presentation, which is why the `when`
 * blocks here contain no logic beyond building the key.
 */
internal class ServiceLogFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.ServiceLog.List> { key -> ServiceLogListRoute(key, navigationManager) }
        entry<OdoDestination.ServiceLog.Detail> { key -> ServiceLogDetailRoute(key, navigationManager) }
        entry<OdoDestination.ServiceLog.AddEdit> { key -> ServiceLogFormRoute(key, navigationManager) }
        entry<OdoDestination.ServiceLog.ReportOvercharge> { key -> ServiceLogReportOverchargeRoute(key, navigationManager) }
        entry<OdoDestination.ServiceLog.Share>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) { key ->
            ShareRecordRoute(key, navigationManager)
        }
    }
}

/**
 * The list route host. Opening a card jumps to its [OdoDestination.ServiceLog.Detail]; the
 * add affordance opens the [OdoDestination.ServiceLog.AddEdit] form for the same car.
 */
@Composable
internal fun ServiceLogListRoute(
    key: OdoDestination.ServiceLog.List,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<ServiceLogListViewModel> { parametersOf(CarId(key.carId)) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ServiceLogListEffect.OpenEntry ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.Detail(logId = effect.id.value, carId = key.carId))

            ServiceLogListEffect.OpenAddForm ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.AddEdit(carId = key.carId))

            ServiceLogListEffect.OpenBillScanner ->
                navigationManager.navigateTo(OdoDestination.BillScanner.Capture())

            ServiceLogListEffect.OpenShareRecord ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.Share(carId = key.carId))

            // TODO(filters): an advanced-filters sheet. The three chips cover the MVP, so
            //  the tap is collected rather than opening a screen that filters nothing.
            ServiceLogListEffect.OpenFilters -> Unit

            ServiceLogListEffect.NavigateBack -> navigationManager.back()
        }
    }

    ServiceLogListScreen(state = state, onEvent = viewModel::onEvent)
}

/** The detail route host — the combined fairness + resale-proof view for one entry. */
@Composable
internal fun ServiceLogDetailRoute(
    key: OdoDestination.ServiceLog.Detail,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<ServiceLogDetailViewModel> {
        parametersOf(CarId(key.carId), ServiceLogId(key.logId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leaving while the fairness coach mark is up releases the arbiter's grant without
    // burning the hook's one showing — the owner never answered it (#230).
    DisposableEffect(Unit) {
        onDispose { viewModel.onEvent(ServiceLogDetailEvent.FairnessShowcaseLeft) }
    }
    // Held here because a picker is a platform activity, not a ViewModel call: the launcher
    // has to be remembered in the composition that will still be alive when it returns.
    val launchBillPicker = rememberFilePicker { pickedRef ->
        pickedRef?.let { viewModel.onEvent(ServiceLogDetailEvent.BillPicked(it)) }
    }
    // Read here rather than in the effect handler, which is not a composable and so cannot
    // reach a string resource.
    val billTitle = stringResource(Res.string.sl_detail_bill_attached)

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ServiceLogDetailEffect.PickBillPhoto -> launchBillPicker()

            is ServiceLogDetailEffect.PreviewBill -> navigationManager.navigateTo(
                OdoDestination.FilePreview(
                    storageKey = effect.storageKey,
                    title = billTitle,
                    // A bill attached on another phone syncs its row here but not its photo.
                    remoteBucket = OdoDestination.FilePreview.BUCKET_BILL_PHOTOS,
                ),
            )

            is ServiceLogDetailEffect.OpenFairness ->
                navigationManager.navigateTo(
                    OdoDestination.Fairness(
                        items = effect.lines.map {
                            FairnessLineInput(
                                label = it.label.orEmpty(),
                                category = it.categoryName,
                                amountPaise = it.amountPaise,
                            )
                        },
                        logId = effect.id.value,
                        carId = key.carId,
                    ),
                )

            // From an entry's detail the sheet shares that entry's bill, not the whole
            // record — the logId is what flips the document.
            ServiceLogDetailEffect.OpenShareRecord ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.Share(carId = key.carId, logId = key.logId))

            is ServiceLogDetailEffect.OpenReportOvercharge ->
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.ReportOvercharge(logId = effect.id.value, carId = key.carId),
                )

            is ServiceLogDetailEffect.OpenEditForm ->
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.AddEdit(carId = key.carId, editLogId = effect.id.value),
                )

            // A deleted entry has nothing left to show, so both leave the screen.
            ServiceLogDetailEffect.Deleted, ServiceLogDetailEffect.NavigateBack -> navigationManager.back()
        }
    }

    ServiceLogDetailScreen(state = state, onEvent = viewModel::onEvent)
}

/** The report-overcharge route host. Done, back and a filed report all pop the screen. */
@Composable
internal fun ServiceLogReportOverchargeRoute(
    key: OdoDestination.ServiceLog.ReportOvercharge,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<ReportOverchargeViewModel> { parametersOf(ServiceLogId(key.logId)) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ReportOverchargeEffect.NavigateBack -> navigationManager.back()
        }
    }

    ReportOverchargeScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The add/edit form route host. Both a successful save and back simply pop the form; the
 * underlying list observes the repository, so it reflects the change on return.
 */
@Composable
internal fun ServiceLogFormRoute(
    key: OdoDestination.ServiceLog.AddEdit,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<ServiceLogFormViewModel> {
        parametersOf(CarId(key.carId), key.editLogId?.let(::ServiceLogId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ServiceLogFormEffect.Saved -> navigationManager.back()

            ServiceLogFormEffect.OpenBillScanner ->
                navigationManager.navigateTo(OdoDestination.BillScanner.Capture())

            // Attaching happens on the entry's detail screen, where there is an entry to
            // attach to. The form's slot is a prompt, not a second way in: a photo picked
            // before the entry exists would have nothing to be copied against.
            ServiceLogFormEffect.AttachBill -> Unit

            ServiceLogFormEffect.NavigateBack -> navigationManager.back()
        }
    }

    ServiceLogFormScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The share-sheet route host.
 *
 * Both effects are platform work the ViewModel cannot do: laying a document out needs a
 * renderer with a UI host, and presenting a share sheet needs whatever is hosting the UI.
 * The ViewModel builds the document and decides where it goes; this runs it.
 */
@Composable
internal fun ShareRecordRoute(key: OdoDestination.ServiceLog.Share, navigationManager: NavigationManager) {
    val viewModel = koinViewModel<ShareRecordViewModel> {
        // Absent for a whole-record share; present when one entry's bill is going out.
        parametersOf(CarId(key.carId), key.logId?.let(::ServiceLogId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val htmlToPdf = rememberHtmlToPdf()
    val shareFile = rememberFileSharer()
    // Rendering suspends and CollectEffects hands over a plain lambda, so the render runs on
    // the composition's own scope. It is cancelled with the sheet, which is what should
    // happen to a document nobody is waiting for any more.
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ShareRecordEffect.RenderDocument -> scope.launch {
                viewModel.onEvent(
                    ShareRecordEvent.Rendered(
                        bytes = htmlToPdf(effect.html, effect.documentName),
                        target = effect.target,
                    ),
                )
            }

            is ShareRecordEffect.ShareFile ->
                shareFile(effect.storageKey, ShareMimeType.PDF, effect.title)

            // Close the sheet first: leaving it stacked under the paywall would put the
            // owner back on a locked export after they subscribed, instead of the screen
            // they came from.
            ShareRecordEffect.OpenPaywall -> {
                navigationManager.back()
                navigationManager.navigateTo(OdoDestination.Paywall(trigger = PAYWALL_TRIGGER_EXPORT))
            }
        }
    }

    ShareRecordSheetContent(state = state, onEvent = viewModel::onEvent)
}

/** Where the paywall was opened from. A shipped analytics value — do not reword it. */
private const val PAYWALL_TRIGGER_EXPORT = "RECORD_EXPORT"
