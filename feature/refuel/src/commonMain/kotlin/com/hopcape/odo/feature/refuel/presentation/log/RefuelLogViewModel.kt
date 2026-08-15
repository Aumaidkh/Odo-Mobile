package com.hopcape.odo.feature.refuel.presentation.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.refuel.domain.toInput
import com.hopcape.odo.feature.refuel.domain.usecase.BuildFillDraftUseCase
import com.hopcape.odo.feature.refuel.presentation.RefuelTelemetry
import com.hopcape.odo.feature.refuel.presentation.logged.shortLabel
import com.hopcape.odo.feature.refuel.presentation.rupeeText
import com.hopcape.odo.feature.refuel.presentation.toPaise
import com.hopcape.odo.feature.refuel.presentation.unitText
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_error_no_car
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the "log a fill" form — the channel that works everywhere.
 *
 * There is no detection here and no camera: just the owner's own history, arranged so that
 * the only thing left to supply is what they paid. It is the floor the whole feature stands
 * on, because a market with no UPI and a pump with an unreadable display still leave this
 * working.
 *
 * Nothing is written from this screen. It builds a draft and hands it to the confirm surface,
 * the same as every other channel, so there is one place a fill is ever created.
 */
internal class RefuelLogViewModel(
    private val activeCar: ActiveCarProvider,
    private val buildDraft: BuildFillDraftUseCase,
    private val fills: FuelFillRepository,
    private val detection: RefuelDetectionStore,
    private val telemetry: RefuelTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(RefuelLogUiState())
    val state: StateFlow<RefuelLogUiState> = _state.asStateFlow()

    private val _effects = Channel<RefuelLogEffect>(Channel.BUFFERED)
    val effects: Flow<RefuelLogEffect> = _effects.receiveAsFlow()

    private var draft: FuelFillDraft? = null

    init {
        viewModelScope.launch(telemetry.op(OP_LOAD)) {
            val carId = activeCar.activeCarId.value
            if (carId == null) {
                _state.update { it.copy(loading = false, error = UiText(Res.string.rf_error_no_car)) }
                return@launch
            }

            val predict = detection.settings().predictOdometer
            val built = buildDraft.prefilled(carId, predictOdometer = predict)
            draft = built

            _state.update {
                it.copy(
                    loading = false,
                    stationName = built.stationName,
                    rateLabel = built.pricePerUnit
                        ?.let { rate -> "${rupeeText(rate.paise)}/${built.unit.shortLabel()}" }
                        .orEmpty(),
                    odometerKm = built.odometerKm,
                    odometerPredicted = built.odometerOrigin == FieldOrigin.PREDICTED,
                    quickAmounts = quickAmountsFor(carId),
                )
            }
        }
    }

    fun onEvent(event: RefuelLogEvent) = when (event) {
        is RefuelLogEvent.AmountChanged -> setAmount(event.text)
        is RefuelLogEvent.QuickAmountTapped -> setAmount(rupeeText(event.paise))
        RefuelLogEvent.DoneTapped -> submit()
        RefuelLogEvent.ScanPumpTapped -> {
            _effects.trySend(RefuelLogEffect.OpenPumpScanner)
            Unit
        }
    }

    /**
     * Take the amount and show what it buys.
     *
     * The quantity is worked out through the domain draft rather than here, so the figure on
     * this screen and the one on the confirm surface come from the same arithmetic.
     */
    private fun setAmount(text: String) {
        val current = draft ?: return
        val updated = current.copy(
            amount = toPaise(text)?.let { Amount.of(it).getOrNull() },
            amountOrigin = FieldOrigin.TYPED,
            quantityMilli = null,
            quantityOrigin = FieldOrigin.UNKNOWN,
        ).completed()
        draft = updated

        _state.update {
            it.copy(
                amount = text,
                error = null,
                quantityLabel = updated.quantityMilli
                    ?.let { milli -> "${unitText(milli)} ${updated.unit.shortLabel()}" },
            )
        }
    }

    private fun submit() {
        val current = draft ?: return
        _effects.trySend(RefuelLogEffect.Confirm(current.toInput()))
    }

    /**
     * The owner's three most common past amounts, largest first.
     *
     * Their own figures rather than round numbers: someone who always fills ₹2,000 gains
     * nothing from a ₹500 chip, and the chips are only worth the space if one of them is
     * usually the right answer. An owner with no history gets none, which is correct — there
     * is nothing to offer yet.
     */
    private suspend fun quickAmountsFor(carId: com.hopcape.odo.core.domain.car.model.CarId): List<QuickAmount> =
        fills.observeForCar(carId).first()
            .groupingBy { it.amount.paise }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenByDescending { it.key })
            .take(MAX_QUICK_AMOUNTS)
            .map { (paise, _) -> QuickAmount(paise = paise, label = rupeeText(paise)) }

    private companion object {
        const val OP_LOAD = "refuel_log_load"
        const val MAX_QUICK_AMOUNTS = 3
    }
}
