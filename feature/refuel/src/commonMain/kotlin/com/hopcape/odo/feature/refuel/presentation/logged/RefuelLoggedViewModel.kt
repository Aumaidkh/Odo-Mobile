package com.hopcape.odo.feature.refuel.presentation.logged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.feature.refuel.domain.usecase.GetTankInsightUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.TankComparison
import com.hopcape.odo.feature.refuel.presentation.RefuelTelemetry
import com.hopcape.odo.feature.refuel.presentation.rupeeText
import com.hopcape.odo.feature.refuel.presentation.unitText
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_insight_better
import com.hopcape.odo.feature.refuel.resources.rf_insight_first
import com.hopcape.odo.feature.refuel.resources.rf_insight_mileage
import com.hopcape.odo.feature.refuel.resources.rf_insight_typical
import com.hopcape.odo.feature.refuel.resources.rf_insight_worse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * State holder for the success screen.
 *
 * It reads the fill back out of storage by id rather than being handed the numbers, so what
 * the owner sees is the record that now exists. If the write stored something different from
 * what the form showed, this is where that becomes visible instead of being papered over.
 *
 * The mileage line is the only thing the owner gets *back* for logging, and the first
 * measured figure the app has ever been able to show — everything before it came from a
 * table of typical numbers by fuel type.
 */
internal class RefuelLoggedViewModel(
    private val fillId: String,
    private val activeCar: ActiveCarProvider,
    private val fills: FuelFillRepository,
    private val tankInsight: GetTankInsightUseCase,
    private val telemetry: RefuelTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(RefuelLoggedUiState())
    val state: StateFlow<RefuelLoggedUiState> = _state.asStateFlow()

    private val _effects = Channel<RefuelLoggedEffect>(Channel.BUFFERED)
    val effects: Flow<RefuelLoggedEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch(telemetry.op(OP_LOAD)) {
            val carId = activeCar.activeCarId.value
            if (carId == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }

            val fill = fills.observeForCar(carId).first().firstOrNull { it.id.value == fillId }
            if (fill == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }

            val insight = tankInsight(carId)
            insight?.let { telemetry.tankInsightShown(it.comparison.name) }

            _state.update {
                it.copy(
                    loading = false,
                    source = fill.entrySource,
                    stationName = fill.stationName,
                    quantityLabel = "${unitText(fill.quantityMilli)} ${fill.unit.shortLabel()}",
                    rateLabel = fill.pricePerUnit
                        ?.let { rate -> "${rupeeText(rate.paise)}/${fill.unit.shortLabel()}" }
                        .orEmpty(),
                    odometerKm = fill.odometer?.km,
                    mileage = insight?.let { reading ->
                        UiText(
                            Res.string.rf_insight_mileage,
                            listOf(reading.distancePerUnit.mileageLabel(reading.unit)),
                        )
                    },
                    mileageComparison = insight?.comparison?.message(),
                )
            }
        }
    }

    fun onEvent(event: RefuelLoggedEvent) = when (event) {
        RefuelLoggedEvent.DoneTapped -> {
            _effects.trySend(RefuelLoggedEffect.Close)
            Unit
        }

        RefuelLoggedEvent.ViewTimelineTapped -> {
            _effects.trySend(RefuelLoggedEffect.OpenTimeline)
            Unit
        }
    }

    /**
     * Mileage to one decimal place — "16.4 km/L".
     *
     * One place because that is the resolution the figure actually has: it comes out of two
     * odometer readings in whole kilometres, and a second decimal would claim a precision
     * the inputs never had.
     */
    private fun Double.mileageLabel(unit: FuelUnit): String {
        val rounded = (this * 10).roundToInt() / 10.0
        val whole = rounded.toInt()
        val tenth = ((rounded - whole) * 10).roundToInt()
        return "$whole.$tenth km/${unit.shortLabel()}"
    }

    private fun TankComparison.message(): UiText = when (this) {
        TankComparison.Better -> UiText(Res.string.rf_insight_better)
        TankComparison.Typical -> UiText(Res.string.rf_insight_typical)
        TankComparison.Worse -> UiText(Res.string.rf_insight_worse)
        TankComparison.NoBaseline -> UiText(Res.string.rf_insight_first)
    }

    private companion object {
        const val OP_LOAD = "refuel_logged_load"
    }
}

/** The unit as it appears inside a figure, where a full word would not fit. */
internal fun FuelUnit.shortLabel(): String = when (this) {
    FuelUnit.LITRE -> "L"
    FuelUnit.KILOGRAM -> "kg"
    FuelUnit.KILOWATT_HOUR -> "kWh"
}
