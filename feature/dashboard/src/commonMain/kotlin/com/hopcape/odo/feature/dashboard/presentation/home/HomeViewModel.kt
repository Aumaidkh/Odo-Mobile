package com.hopcape.odo.feature.dashboard.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.feature.dashboard.domain.model.HomeSnapshot
import com.hopcape.odo.feature.dashboard.domain.usecase.ObserveHomeUseCase
import com.hopcape.odo.feature.dashboard.presentation.state.Loadable
import com.hopcape.odo.feature.dashboard.presentation.state.valueOrNull
import com.hopcape.odo.feature.dashboard.resources.Res
import com.hopcape.odo.feature.dashboard.resources.hm_error_load_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the Home tab. Holds [HomeUiState], consumes [HomeEvent]s, and emits
 * [HomeEffect]s for the route host to navigate on.
 *
 * The car comes from [ActiveCarProvider] rather than a navigation key: this is a bottom tab,
 * reached without naming a car, and every per-car surface answering "which car?" for itself
 * is how the app ends up showing someone else's.
 *
 * Where a tap goes is decided here, not in the composable: the attention card leads to the
 * vault or to the service log depending on what it is about, and that is a fact about the
 * state rather than about the layout.
 */
internal class HomeViewModel(
    activeCar: ActiveCarProvider,
    observeHome: ObserveHomeUseCase,
    private val detection: RefuelDetectionStore,
    private val bonds: VehicleBondStore,
    private val tracker: TripTracker,
    private val showcase: ShowcaseArbiter,
    private val entitlements: EntitlementSource,
    private val telemetry: HomeTelemetry,
) : ViewModel() {

    /** True while the SCAN coach mark holds the arbiter's grant. */
    private val scanShowcaseVisible = MutableStateFlow(false)

    /** One ask per visit — reset when the surface is left, so the next visit may ask again. */
    private var scanShowcaseRequested = false

    /** True while the health coach mark holds the arbiter's grant (#232). */
    private val healthShowcaseVisible = MutableStateFlow(false)

    private var healthShowcaseRequested = false

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    /** The car the dashboard is of, held for the per-car destinations it opens. */
    private var carId: CarId? = null

    /** Guards the opened event so a re-read does not count a second visit. */
    private var reportedOpen = false

    /**
     * The car's dashboard.
     *
     * A failed read becomes [Loadable.Failed] rather than an empty dashboard: the local DB
     * is the source of truth, so a read that fails means the record is unknown, and a car
     * with a full history must not be shown a setup checklist because a query broke.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = activeCar.activeCarId
        .onEach { carId = it }
        .flatMapLatest { id ->
            // No car yet means setup never finished. There is nothing truthful to show
            // about a car that does not exist, so Home asks for one instead.
            if (id == null) {
                flowOf(HomeUiState(content = Loadable.Ready(HomeContent())))
            } else {
                observeHome(id).map { HomeUiState(content = Loadable.Ready(it.toContent())) }
            }
        }
        // Combined rather than folded into the snapshot: the offer is a device setting, and a
        // dashboard read that failed should not decide whether it is shown.
        .combine(offerAutoDetect()) { ui, offer -> ui.copy(offerAutoDetect = offer) }
        // Same shape as the auto-detect offer: device state, not car state.
        .combine(offerAutoOdometer()) { ui, offer -> ui.copy(offerAutoOdometer = offer) }
        .combine(scanShowcaseVisible) { ui, visible -> ui.copy(scanShowcase = visible) }
        .combine(healthShowcaseVisible) { ui, visible -> ui.copy(healthShowcase = visible) }
        // Read only to pick the Pro-gated coach marks' copy — never to hide them.
        .combine(entitlements.observe().map { it.plan == Plan.PRO }.catch { emit(false) }) { ui, pro ->
            ui.copy(proPlan = pro)
        }
        .onEach(::maybeRequestScanShowcase)
        .onEach(::maybeRequestHealthShowcase)
        .onEach(::reportOpened)
        .catch { cause ->
            telemetry.readFailed(cause)
            emit(HomeUiState(content = Loadable.Failed(UiText(Res.string.hm_error_load_failed))))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = HomeUiState(),
        )

    /**
     * Whether automatic logging is worth offering: built, and not already on.
     *
     * Never offered on a build where detection cannot run, so the card can never lead to a
     * screen that would ask for a permission this app does not declare.
     */
    /**
     * The SCAN hook's due-condition (#228): a car exists and nothing has been logged —
     * an owner who has already scanned does not need telling. Asked once per visit; the
     * arbiter answers, and a denial simply waits for the next visit.
     */
    private suspend fun maybeRequestScanShowcase(ui: HomeUiState) {
        if (scanShowcaseRequested) return
        val content = ui.content.valueOrNull ?: return
        val due = !content.hasNoCar && !content.setup.hasServiceLogs
        if (!due) return
        scanShowcaseRequested = true
        if (showcase.request(ShowcaseHookId.SCAN_BUTTON)) {
            scanShowcaseVisible.value = true
        }
    }

    /**
     * The health hook's due-condition (#232): the score is on screen — the scored
     * dashboard, not the checklist. What the number responds to is a screen away, and
     * nothing else suggests it is actionable. If the SCAN hook is also due on the same
     * frame, the arbiter grants exactly one; the other waits for its next visit.
     */
    private suspend fun maybeRequestHealthShowcase(ui: HomeUiState) {
        if (healthShowcaseRequested) return
        val content = ui.content.valueOrNull ?: return
        val due = !content.hasNoCar && !content.isNewUser
        if (!due) return
        healthShowcaseRequested = true
        if (showcase.request(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN)) {
            healthShowcaseVisible.value = true
        }
    }

    private fun offerAutoDetect(): Flow<Boolean> =
        if (!FeatureFlags.SMART_REFUEL_DETECT_ENABLED) {
            flowOf(false)
        } else {
            detection.observeSettings().map { !it.detectEnabled }.catch { emit(false) }
        }

    /**
     * Whether the auto odometer is worth pitching: built, and not already set up.
     *
     * "Set up" is the same fact the garage's `ObserveAutoOdometerCardState` reads — a bond
     * exists and tracking is on. [VehicleBondStore.bond] is a plain suspend getter (no
     * bond-change stream exists), re-read whenever the enabled flag moves — which is
     * exactly when enrollment finishes, so the card leaves the dashboard on its own.
     * A failed read hides the offer: a card is not worth a crashed dashboard.
     */
    private fun offerAutoOdometer(): Flow<Boolean> =
        if (!FeatureFlags.AUTO_ODOMETER_ENABLED) {
            flowOf(false)
        } else {
            tracker.isEnabled.map { enabled -> !(enabled && bonds.bond() != null) }.catch { emit(false) }
        }

    fun onEvent(event: HomeEvent) = when (event) {
        HomeEvent.BreakdownTapped -> {
            telemetry.breakdownOpened()
            send(HomeEffect.OpenHealthScore)
        }

        HomeEvent.AttentionTapped -> openAttention()

        HomeEvent.TimelineTapped -> {
            telemetry.timelineOpened()
            send(HomeEffect.OpenTimeline)
        }

        HomeEvent.RecentTapped -> openRecent()

        HomeEvent.BellTapped -> send(HomeEffect.OpenReminders)

        HomeEvent.ProfileTapped -> send(HomeEffect.OpenProfile)

        HomeEvent.ScanBillTapped -> {
            telemetry.scanBillTapped(fromChecklist = content()?.isNewUser == true)
            send(HomeEffect.OpenScanner)
        }

        HomeEvent.LogFillTapped -> send(HomeEffect.OpenLogFill)

        // Never a paywall now (#251): automatic logging is free for as long as the owner
        // keeps the permission granted, so the card only ever opens the explanation.
        HomeEvent.AutoDetectTapped -> send(HomeEffect.OpenAutoDetect)

        HomeEvent.AutoOdometerTapped -> {
            telemetry.autoOdometerTapped()
            send(HomeEffect.OpenAutoOdometer)
        }

        HomeEvent.AddDocumentsTapped -> {
            telemetry.addDocumentsTapped()
            send(HomeEffect.OpenAddDocument)
        }

        HomeEvent.AddCarTapped -> {
            telemetry.addCarTapped()
            send(HomeEffect.OpenAddCar)
        }

        HomeEvent.ScanShowcaseDismissed -> {
            scanShowcaseVisible.value = false
            viewModelScope.launch { showcase.dismissed(ShowcaseHookId.SCAN_BUTTON) }
        }

        HomeEvent.ScanShowcaseActedOn -> {
            scanShowcaseVisible.value = false
            viewModelScope.launch { showcase.actedOn(ShowcaseHookId.SCAN_BUTTON) }
            send(HomeEffect.OpenScanner)
        }

        // Not seen: the owner never answered — the redirect or tab switch did. The hook
        // keeps its one showing, and the reset lets the next visit ask again.
        HomeEvent.ScanShowcaseLeft -> {
            if (scanShowcaseVisible.value) showcase.surfaceLeft(ShowcaseHookId.SCAN_BUTTON)
            scanShowcaseVisible.value = false
            scanShowcaseRequested = false
        }

        HomeEvent.HealthShowcaseDismissed -> {
            healthShowcaseVisible.value = false
            viewModelScope.launch { showcase.dismissed(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN) }
            Unit
        }

        HomeEvent.HealthShowcaseActedOn -> {
            healthShowcaseVisible.value = false
            viewModelScope.launch { showcase.actedOn(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN) }
            send(HomeEffect.OpenHealthScore)
        }

        HomeEvent.HealthShowcaseLeft -> {
            if (healthShowcaseVisible.value) showcase.surfaceLeft(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN)
            healthShowcaseVisible.value = false
            healthShowcaseRequested = false
        }
    }

    /**
     * Where the attention card leads: a paper is renewed in the vault, a service is dealt
     * with in the log. Sending both to the vault — which is what the card did before it had
     * any data behind it — leaves an overdue service with nowhere to go.
     */
    private fun openAttention() {
        val attention = content()?.attention ?: return
        telemetry.attentionTapped(kind = attention::class.simpleName.orEmpty())
        when (attention) {
            is CarAttention.DocumentLapsed, is CarAttention.DocumentExpiring ->
                send(HomeEffect.OpenVault)

            is CarAttention.ServiceOverdue, is CarAttention.ServiceDue ->
                carId?.let { send(HomeEffect.OpenServiceLog(carId = it.value)) }
        }
    }

    /** Only a logged service has a detail screen; the other rows are read-only history. */
    private fun openRecent() {
        val service = content()?.recent as? ActivityEvent.Service ?: return
        val car = carId ?: return
        telemetry.recentOpened()
        send(HomeEffect.OpenService(logId = service.id.value, carId = car.value))
    }

    /**
     * The dashboard the tab opened on, reported once per visit. An empty checklist and a
     * scored car are different product problems, so the band, the setup progress and
     * whether anything needed attention all ride along.
     */
    private fun reportOpened(state: HomeUiState) {
        val content = state.content.valueOrNull ?: return
        if (reportedOpen) return
        reportedOpen = true
        telemetry.homeOpened(
            band = content.band.name,
            isNewUser = content.isNewUser,
            hasAttention = content.attention != null,
            setupDone = content.setup.doneCount,
        )
    }

    private fun content(): HomeContent? = state.value.content.valueOrNull

    private fun send(effect: HomeEffect) {
        _effects.trySend(effect)
        Unit
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Domain snapshot to display state. Decided here, once. */
private fun HomeSnapshot.toContent(): HomeContent = HomeContent(
    userName = ownerName.orEmpty(),
    carName = car?.displayName.orEmpty(),
    odometer = odometer,
    score = score.total,
    band = score.band,
    scoreDelta = scoreDelta,
    perKm = cost.perKm,
    costTrend = costTrend,
    overchargeTotal = savings.overchargeTotal,
    overchargesCaught = savings.overchargesCaught,
    attention = attention,
    insight = insight,
    recent = recent,
    tank = tank,
    setup = setup,
    isNewUser = isNewUser,
)
