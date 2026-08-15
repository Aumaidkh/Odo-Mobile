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
import com.hopcape.odo.feature.dashboard.domain.model.HomeSnapshot
import com.hopcape.odo.feature.dashboard.domain.usecase.ObserveHomeUseCase
import com.hopcape.odo.feature.dashboard.presentation.state.Loadable
import com.hopcape.odo.feature.dashboard.presentation.state.valueOrNull
import com.hopcape.odo.feature.dashboard.resources.Res
import com.hopcape.odo.feature.dashboard.resources.hm_error_load_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
    private val telemetry: HomeTelemetry,
) : ViewModel() {

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
    private fun offerAutoDetect(): Flow<Boolean> =
        if (!FeatureFlags.SMART_REFUEL_DETECT_ENABLED) {
            flowOf(false)
        } else {
            detection.observeSettings().map { !it.detectEnabled }.catch { emit(false) }
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

        HomeEvent.AutoDetectTapped -> send(HomeEffect.OpenAutoDetect)

        HomeEvent.AddDocumentsTapped -> {
            telemetry.addDocumentsTapped()
            send(HomeEffect.OpenAddDocument)
        }

        HomeEvent.AddCarTapped -> {
            telemetry.addCarTapped()
            send(HomeEffect.OpenAddCar)
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
    setup = setup,
    isNewUser = isNewUser,
)
