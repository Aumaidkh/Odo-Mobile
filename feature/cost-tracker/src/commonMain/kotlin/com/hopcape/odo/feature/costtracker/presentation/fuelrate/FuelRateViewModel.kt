package com.hopcape.odo.feature.costtracker.presentation.fuelrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.costtracker.domain.usecase.ClearFuelRateUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.FuelRateSnapshot
import com.hopcape.odo.feature.costtracker.domain.usecase.GetFuelRateUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.SetFuelRateUseCase
import com.hopcape.odo.feature.costtracker.presentation.CostTrackerTelemetry
import com.hopcape.odo.feature.costtracker.resources.Res
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_error_range
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_error_save
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the fuel-rate sheet — the owner stating what their pump charges.
 *
 * The field is rupees as typed and the domain stores paise, so [toPaise] is the only place
 * that conversion happens. Anything the parser cannot read is treated as out of range: the
 * use case is still the one that decides what a plausible price is, and having two opinions
 * about that is how a field starts rejecting what the domain would accept.
 */
internal class FuelRateViewModel(
    private val activeCar: ActiveCarProvider,
    private val getFuelRate: GetFuelRateUseCase,
    private val setFuelRate: SetFuelRateUseCase,
    private val clearFuelRate: ClearFuelRateUseCase,
    private val telemetry: CostTrackerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(FuelRateUiState())
    val state: StateFlow<FuelRateUiState> = _state.asStateFlow()

    private val _effects = Channel<FuelRateEffect>(Channel.BUFFERED)
    val effects: Flow<FuelRateEffect> = _effects.receiveAsFlow()

    /** The car's fuel, read with the price — every write is against it. */
    private var fuelType: FuelType = FuelType.PETROL

    /** Once the owner has typed, the field is theirs and no re-read may overwrite it. */
    private var edited = false

    init {
        viewModelScope.launch(telemetry.op(OP_LOAD)) {
            activeCar.activeCarId.value?.let { carId ->
                getFuelRate.observe(carId).collect(::apply)
            }
        }
    }

    fun onEvent(event: FuelRateEvent) = when (event) {
        is FuelRateEvent.PriceChanged -> {
            edited = true
            _state.update { it.copy(price = event.text, error = null) }
        }
        FuelRateEvent.SaveTapped -> save()
        FuelRateEvent.ClearTapped -> clear()
    }

    /**
     * Prefill with the price in force, whoever set it. Odo's own figure is a better starting
     * point than an empty field: most owners are correcting it by a rupee or two.
     *
     * A re-read leaves the field alone once the owner has typed into it — the price behind
     * the sheet can change while they are mid-edit, and their keystrokes win.
     */
    private fun apply(snapshot: FuelRateSnapshot?) {
        if (snapshot == null) return
        fuelType = snapshot.fuelType
        _state.update {
            it.copy(
                unit = snapshot.price?.unit ?: it.unit,
                price = if (edited) it.price else snapshot.price?.pricePerUnit?.let(::toRupeeText).orEmpty(),
                canClear = snapshot.price?.source == FuelPriceSource.OWNER,
            )
        }
    }

    private fun save() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch(telemetry.op(OP_SAVE)) {
            setFuelRate(fuelType, toPaise(_state.value.price))
                .onRight {
                    telemetry.fuelRateSaved(fuelType.name)
                    // The sheet is a navigation destination, so this ViewModel outlives the
                    // visit that saved. Leaving `saving` set would reopen it with every
                    // control disabled, and leaving `edited` set would reopen it on the old
                    // text instead of the rate that was just stored.
                    edited = false
                    _state.update { it.copy(saving = false) }
                    _effects.trySend(FuelRateEffect.Dismiss)
                }
                .onLeft { error ->
                    telemetry.fuelRateRefused(error::class.simpleName ?: UNKNOWN)
                    _state.update { it.copy(saving = false, error = error.toMessage()) }
                }
        }
    }

    private fun clear() {
        viewModelScope.launch(telemetry.op(OP_CLEAR)) {
            clearFuelRate(fuelType)
                .onRight {
                    telemetry.fuelRateCleared(fuelType.name)
                    edited = false
                    _state.update { it.copy(saving = false, error = null) }
                    _effects.trySend(FuelRateEffect.Dismiss)
                }
                .onLeft { error ->
                    telemetry.fuelRateRefused(error::class.simpleName ?: UNKNOWN)
                    _state.update { it.copy(error = UiText(Res.string.ct_fuel_rate_error_save)) }
                }
        }
    }

    private fun DomainError.toMessage(): UiText = when (this) {
        is DomainError.FuelPriceOutOfRange -> UiText(
            Res.string.ct_fuel_rate_error_range,
            listOf(rupeesOf(minPaise), rupeesOf(maxPaise)),
        )

        else -> UiText(Res.string.ct_fuel_rate_error_save)
    }

    private companion object {
        const val OP_LOAD = "fuel_rate_load"
        const val OP_SAVE = "fuel_rate_save"
        const val OP_CLEAR = "fuel_rate_clear"
        const val UNKNOWN = "Unknown"
    }
}

/**
 * Rupees as typed into paise — "104.4" and "104.40" both become 10,440.
 *
 * `null` for anything that is not a plain amount (letters, two dots, more than two decimal
 * places). The use case turns that into the same out-of-range error a wild number gets, so
 * the owner sees one message either way.
 */
internal fun toPaise(text: String): Long? {
    val trimmed = text.trim().replace(",", "")
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split('.')
    if (parts.size > 2) return null
    val rupees = parts[0].toLongOrNull() ?: return null
    if (rupees < 0) return null
    val paise = when (val fraction = parts.getOrNull(1)) {
        null, "" -> 0L
        else -> {
            if (fraction.length > 2 || fraction.any { !it.isDigit() }) return null
            fraction.padEnd(2, '0').toLong()
        }
    }
    return rupees * 100 + paise
}

/** Paise back into the field's text — "10440" reads as "104.40". */
private fun toRupeeText(amount: Amount): String = rupeesOf(amount.paise)

private fun rupeesOf(paise: Long): String {
    val whole = paise / 100
    val fraction = (paise % 100).toInt()
    return if (fraction == 0) "$whole" else "$whole.${fraction.toString().padStart(2, '0')}"
}
