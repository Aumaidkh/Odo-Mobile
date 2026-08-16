package com.hopcape.odo.feature.costtracker.presentation.runningcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.text.DistanceArg
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.cost.model.CostShortfall
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.shared.formatMonth
import com.hopcape.odo.core.domain.shared.formatMonthYear
import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod
import com.hopcape.odo.feature.costtracker.domain.model.RunningCostSnapshot
import com.hopcape.odo.feature.costtracker.domain.model.SpendBucket
import com.hopcape.odo.feature.costtracker.domain.usecase.ObserveRunningCostUseCase
import com.hopcape.odo.feature.costtracker.presentation.CostTrackerTelemetry
import com.hopcape.odo.feature.costtracker.presentation.state.Loadable
import com.hopcape.odo.feature.costtracker.presentation.state.valueOrNull
import com.hopcape.odo.feature.costtracker.resources.Res
import com.hopcape.odo.feature.costtracker.resources.ct_bar_month
import com.hopcape.odo.feature.costtracker.resources.ct_bar_range
import com.hopcape.odo.feature.costtracker.resources.ct_error_load_failed
import com.hopcape.odo.feature.costtracker.resources.ct_no_rate_distance
import com.hopcape.odo.feature.costtracker.resources.ct_no_rate_readings
import com.hopcape.odo.feature.costtracker.resources.ct_period_range
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the running-cost screen. Holds [RunningCostUiState] and consumes
 * [RunningCostEvent]s; the screen only reads, so there are no effects.
 *
 * The car comes from [ActiveCarProvider] rather than a navigation key: this is a tab,
 * reached without naming a car, and every per-car surface answering "which car?" for itself
 * is how the app ends up opening someone else's.
 */
internal class RunningCostViewModel(
    activeCar: ActiveCarProvider,
    observeRunningCost: ObserveRunningCostUseCase,
    settings: AppSettingsRepository,
    entitlements: EntitlementSource,
    private val showcase: ShowcaseArbiter,
    private val telemetry: CostTrackerTelemetry,
) : ViewModel() {

    /**
     * Whether the analysis under the headline is locked (#247).
     *
     * A failed entitlement read locks it: refusing to show a paid surface is recoverable,
     * giving it away by accident is not. The headline itself is never gated either way.
     */
    private val analysisLocked = entitlements.observe()
        .map { !it.has(ProFeature.COST_ANALYSIS) }
        .catch { emit(true) }

    private val period = MutableStateFlow(CostPeriod.Y1)

    /** True while the odometer coach mark holds the arbiter's grant (#229). */
    private val odometerShowcaseVisible = MutableStateFlow(false)

    /** One ask per visit — reset when the surface is left, so the next visit may ask again. */
    private var odometerShowcaseRequested = false

    private val _effects = Channel<RunningCostEffect>(Channel.BUFFERED)
    val effects: Flow<RunningCostEffect> = _effects.receiveAsFlow()

    /** Guards the opened event so a re-read does not count a second visit. */
    private var reportedOpen = false

    /**
     * The car's running cost for the chosen period.
     *
     * A failed read becomes [Loadable.Failed] rather than an empty screen: the local DB is
     * the source of truth, so a read that fails means the figures are unreadable, and
     * showing ₹0/km to an owner with a year of history is the worse of the two lies.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RunningCostUiState> = combine(
        activeCar.activeCarId,
        period,
        settings.observe().map { it.fuelEfficiencyUnit }.distinctUntilChanged(),
    ) { carId, chosen, efficiencyUnit -> Triple(carId, chosen, efficiencyUnit) }
        .flatMapLatest { (carId, chosen, efficiencyUnit) ->
            // No car yet means setup has not finished. Nothing truthful can be said about
            // what an absent car costs to run, so the screen says so instead of waiting.
            if (carId == null) {
                flowOf(RunningCostUiState(period = chosen, fuelEfficiencyUnit = efficiencyUnit, noCar = true))
            } else {
                observeRunningCost(carId, chosen).map { snapshot ->
                    RunningCostUiState(
                        period = chosen,
                        content = Loadable.Ready(snapshot.toContent()),
                        fuelEfficiencyUnit = efficiencyUnit,
                    )
                }
            }
        }
        .combine(analysisLocked) { ui, locked -> ui.copy(analysisLocked = locked) }
        .combine(odometerShowcaseVisible) { ui, visible -> ui.copy(odometerShowcase = visible) }
        .onEach(::maybeRequestOdometerShowcase)
        .onEach(::reportOpened)
        .catch { cause ->
            telemetry.readFailed(cause)
            emit(RunningCostUiState(period = period.value, content = Loadable.Failed(UiText(Res.string.ct_error_load_failed))))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = RunningCostUiState(),
        )

    fun onEvent(event: RunningCostEvent) = when (event) {
        is RunningCostEvent.PeriodSelected -> {
            period.value = event.period
            telemetry.periodChanged(event.period.name)
        }

        RunningCostEvent.FuelRateTapped -> {
            _effects.trySend(RunningCostEffect.OpenFuelRate)
            Unit
        }

        RunningCostEvent.UnlockAnalysisTapped -> {
            _effects.trySend(RunningCostEffect.OpenPaywall)
            Unit
        }

        RunningCostEvent.OdometerShowcaseDismissed -> {
            odometerShowcaseVisible.value = false
            viewModelScope.launch { showcase.dismissed(ShowcaseHookId.ODOMETER_CURRENT) }
            Unit
        }

        // Not seen: the owner never answered — a tab switch did. The hook keeps its one
        // showing, and the reset lets the next visit ask again.
        RunningCostEvent.OdometerShowcaseLeft -> {
            if (odometerShowcaseVisible.value) showcase.surfaceLeft(ShowcaseHookId.ODOMETER_CURRENT)
            odometerShowcaseVisible.value = false
            odometerShowcaseRequested = false
        }
    }

    /**
     * The odometer hook's due-condition (#229): the headline is the "not enough yet"
     * explanation — the one hook that fires on a disappointment rather than a discovery,
     * so it fires exactly while the owner is looking at the empty figure and never once a
     * real rate exists.
     */
    private suspend fun maybeRequestOdometerShowcase(ui: RunningCostUiState) {
        if (odometerShowcaseRequested) return
        val headline = ui.content.valueOrNull?.headline ?: return
        if (headline !is CostHeadline.NotEnoughYet) return
        odometerShowcaseRequested = true
        if (showcase.request(ShowcaseHookId.ODOMETER_CURRENT)) {
            odometerShowcaseVisible.value = true
        }
    }

    /**
     * Whether the screen could answer at all, reported once per visit. A cost tracker with
     * no rate to show is the drop-off worth knowing about.
     */
    private fun reportOpened(state: RunningCostUiState) {
        val content = (state.content as? Loadable.Ready)?.value ?: return
        if (reportedOpen) return
        reportedOpen = true
        telemetry.costOpened(
            period = state.period.name,
            hasRate = content.headline is CostHeadline.Rate,
            fuelEstimated = content.fuelNote is FuelNote.Estimated,
            kmDriven = content.distance.km,
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Domain snapshot to display state. Every decision about what to show is made here, once. */
private fun RunningCostSnapshot.toContent(): RunningCostContent = RunningCostContent(
    headline = headline(),
    distance = cost.kmDriven,
    periodRange = UiText(
        Res.string.ct_period_range,
        listOf(formatMonthYear(window.start), formatMonthYear(window.end)),
    ),
    spendBars = bars(),
    avgPerMonth = perMonth(cost.totalSpend, period.months),
    categories = cost.categories.map { CostCategoryRow(it.category, it.spend, it.perKm) },
    totalSpent = cost.totalSpend,
    fuelNote = fuelNote(),
)

private fun RunningCostSnapshot.headline(): CostHeadline {
    val rate = cost.perKm
    return if (rate == null) {
        CostHeadline.NotEnoughYet(shortfallMessage())
    } else {
        CostHeadline.Rate(perKm = rate, trendPercent = trend?.magnitude, trendUp = trend?.isUp == true)
    }
}

private fun RunningCostSnapshot.shortfallMessage(): UiText = when (val reason = cost.shortfall) {
    null, CostShortfall.NoOdometerReadings -> UiText(Res.string.ct_no_rate_readings)
    is CostShortfall.NotEnoughDistance ->
        UiText(Res.string.ct_no_rate_distance, listOf(DistanceArg(reason.requiredKm)))
}

/**
 * The chart's bars. The tallest is highlighted — a peak month is what an owner scans for,
 * and marking it costs nothing when the bar is already drawn.
 */
private fun RunningCostSnapshot.bars(): List<SpendBar> {
    val peak = buckets.maxOfOrNull { it.spend.paise } ?: 0L
    return buckets.map { bucket ->
        SpendBar(
            label = bucket.label(period),
            amount = bucket.spend,
            highlighted = peak > 0 && bucket.spend.paise == peak,
        )
    }
}

private fun SpendBucket.label(period: CostPeriod): UiText =
    if (period.monthsPerBucket == 1) {
        UiText(Res.string.ct_bar_month, listOf(formatMonth(window.end)))
    } else {
        UiText(Res.string.ct_bar_range, listOf(formatMonth(window.start), formatMonth(window.end)))
    }

/**
 * What to say about the fuel half. Never silent: fuel is estimated, never logged, so an
 * owner reading a ₹/km has to be able to tell which part of it Odo guessed.
 */
private fun RunningCostSnapshot.fuelNote(): FuelNote {
    val price = fuelPrice ?: return FuelNote.Missing
    return FuelNote.Estimated(
        pricePerUnit = price.pricePerUnit,
        unit = price.unit,
        city = price.city,
        ownersOwn = price.source == FuelPriceSource.OWNER,
        kmPerUnit = FuelEfficiencyPolicy.kmPerUnit(price.fuelType),
    )
}

/** The period's spend spread evenly over its months — integer paise, like every other total. */
private fun perMonth(total: Amount, months: Int): Amount =
    Amount.of(total.paise / months).getOrElse { Amount.ZERO }
