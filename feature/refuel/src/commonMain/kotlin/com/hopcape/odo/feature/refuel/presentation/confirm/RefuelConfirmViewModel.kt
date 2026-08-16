package com.hopcape.odo.feature.refuel.presentation.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.refuel.PendingFillStore
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.navigation.FuelFillDraftInput
import com.hopcape.odo.feature.refuel.domain.toDomain
import com.hopcape.odo.feature.refuel.domain.usecase.BuildFillDraftUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.IgnoreMerchantUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.LogRefuelUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.ResolvePendingFillUseCase
import com.hopcape.odo.feature.refuel.presentation.RefuelTelemetry
import com.hopcape.odo.feature.refuel.presentation.rupeeText
import com.hopcape.odo.feature.refuel.presentation.toMilli
import com.hopcape.odo.feature.refuel.presentation.toPaise
import com.hopcape.odo.feature.refuel.presentation.unitText
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_error_no_car
import com.hopcape.odo.feature.refuel.resources.rf_error_save
import com.hopcape.odo.feature.refuel.resources.rf_unit_kg
import com.hopcape.odo.feature.refuel.resources.rf_unit_kwh
import com.hopcape.odo.feature.refuel.resources.rf_unit_litre
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * State holder for the confirm surface — the one place every captured fill passes through.
 *
 * It is deliberately ignorant of how the draft was captured. A payment notification, a photo
 * of a pump and the owner's own form all arrive as the same [FuelFillDraftInput], and the
 * only thing this reads off the channel is [FillEntrySource], which decides what the badge
 * says and what gets recorded on the row.
 *
 * The three money fields are kept in step through the domain draft rather than by this class:
 * changing the amount rebuilds the draft and lets [FuelFillDraft.completed] work out the
 * quantity again. Doing that arithmetic here would be a second implementation of it, and the
 * one in the domain is the one that is tested.
 */
internal class RefuelConfirmViewModel(
    private val input: FuelFillDraftInput,
    private val activeCar: ActiveCarProvider,
    private val fuelPrices: FuelPriceProvider,
    private val buildDraft: BuildFillDraftUseCase,
    private val logRefuel: LogRefuelUseCase,
    private val ignoreMerchant: IgnoreMerchantUseCase,
    private val resolvePendingFill: ResolvePendingFillUseCase,
    private val telemetry: RefuelTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(RefuelConfirmUiState())
    val state: StateFlow<RefuelConfirmUiState> = _state.asStateFlow()

    private val _effects = Channel<RefuelConfirmEffect>(Channel.BUFFERED)
    val effects: Flow<RefuelConfirmEffect> = _effects.receiveAsFlow()

    /** The draft as it currently stands. Every edit rebuilds it; confirm writes it. */
    private var draft: FuelFillDraft = input.toDomain()

    /** Whether the owner changed any number a channel supplied. Reported on the write. */
    private var corrected = false

    init {
        viewModelScope.launch(telemetry.op(OP_LOAD)) {
            val carId = activeCar.activeCarId.value
            draft = if (carId == null) draft.completed() else buildDraft(carId, draft)
            _state.update { it.fromDraft(draft) }
            telemetry.confirmOpened(
                source = draft.source.name,
                prefilledFields = draft.knownFieldCount(),
                odometerPredicted = draft.odometerOrigin == FieldOrigin.PREDICTED,
            )
        }
        watchForARate()
    }

    /**
     * Pick up a fuel price the owner sets while this sheet is still open.
     *
     * Without this the "set fuel price" prompt was a dead end: the owner tapped it, set a
     * rate, came back — and the sheet still had no quantity, because the draft was built once
     * in [init] and nothing told it the rate existed now. A bottom sheet closing over another
     * is not a lifecycle resume, so there is no callback to hang this on; the price port emits
     * for exactly this reason.
     *
     * Only ever *fills* the hole. A draft that already has a rate is left alone, so a figure
     * the owner typed here cannot be overwritten by one they set elsewhere afterwards.
     */
    private fun watchForARate() {
        viewModelScope.launch(telemetry.op(OP_LOAD)) {
            fuelPrices.priceChanges().collect {
                if (draft.pricePerUnit != null) return@collect
                val carId = activeCar.activeCarId.value ?: return@collect
                draft = buildDraft(carId, draft)
                _state.update { it.fromDraft(draft) }
            }
        }
    }

    fun onEvent(event: RefuelConfirmEvent) = when (event) {
        is RefuelConfirmEvent.AmountChanged -> edit {
            copy(amount = toPaise(event.text)?.let { Amount.of(it).getOrNull() }, amountOrigin = FieldOrigin.TYPED)
        }

        is RefuelConfirmEvent.RateChanged -> edit {
            copy(
                pricePerUnit = toPaise(event.text)?.let { Amount.of(it).getOrNull() },
                priceOrigin = FieldOrigin.TYPED,
            )
        }

        is RefuelConfirmEvent.QuantityChanged -> edit {
            copy(quantityMilli = toMilli(event.text), quantityOrigin = FieldOrigin.TYPED)
        }

        is RefuelConfirmEvent.OdometerChanged -> edit {
            copy(odometerKm = event.km.toInt(), odometerOrigin = FieldOrigin.TYPED)
        }

        RefuelConfirmEvent.FuelRateTapped -> {
            _effects.trySend(RefuelConfirmEffect.OpenFuelRate)
            Unit
        }

        RefuelConfirmEvent.ConfirmTapped -> confirm()
        RefuelConfirmEvent.RejectTapped -> reject()
    }

    /**
     * Apply one field change, then let the draft work out whatever follows from it.
     *
     * The edited field is cleared out of the *other* two before completing, so a new amount
     * produces a new quantity instead of being ignored because a quantity is already there.
     * Only the derived counterpart is dropped: a rate the owner typed and a quantity a pump
     * printed both stay, because neither was Odo's to invent.
     */
    private fun edit(change: FuelFillDraft.() -> FuelFillDraft) {
        corrected = true
        draft = draft.change().clearDerived().completed()
        _state.update { it.fromDraft(draft).copy(error = null) }
    }

    private fun confirm() {
        if (_state.value.saving) return
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            _state.update { it.copy(error = UiText(Res.string.rf_error_no_car)) }
            return
        }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch(telemetry.op(OP_CONFIRM)) {
            logRefuel(carId, draft)
                .onRight { fill ->
                    telemetry.fillLogged(source = draft.source.name, corrected = corrected)
                    // Close the question this draft came from, if it came from one. A fill
                    // written from a pending detection that stayed open would be offered
                    // again the next time the listener re-read the shade.
                    resolvePending()
                    _state.update { it.copy(saving = false) }
                    _effects.trySend(RefuelConfirmEffect.Logged(fill.id.value))
                }
                .onLeft { errors ->
                    val first = errors.head
                    telemetry.fillRefused(
                        source = draft.source.name,
                        reason = first::class.simpleName ?: UNKNOWN,
                    )
                    _state.update { it.copy(saving = false, error = first.toMessage()) }
                }
        }
    }

    /**
     * The owner said this was not a fuel fill.
     *
     * Nothing is written. For a detected capture the merchant is remembered as one to stop
     * asking about, which is the only way the classifier ever gets better — it cannot learn
     * from a payment it was right about, only from one it was wrong about.
     */
    private fun reject() {
        viewModelScope.launch(telemetry.op(OP_REJECT)) {
            telemetry.captureRejected(draft.source.name)
            if (draft.source == FillEntrySource.DETECTED) {
                draft.stationName?.let { ignoreMerchant(it) }
            }
            resolvePending()
            _effects.trySend(RefuelConfirmEffect.Dismiss)
        }
    }

    /**
     * Mark the detection this draft came from as answered.
     *
     * Matched on the draft rather than on an id passed through the navigation key: the key
     * carries primitives, and threading a pending id through every capture channel would put a
     * field on the shared type that only one of them ever sets. A detection is identified by
     * what it detected, which is exactly what the draft still holds.
     */
    private suspend fun resolvePending() {
        if (draft.source != FillEntrySource.DETECTED) return
        // Also takes the notification down. Leaving it up is what let a confirmed fill be
        // confirmed a second time from the shade.
        resolvePendingFill.byContents(merchant = draft.stationName, amountPaise = draft.amount?.paise)
    }

    private fun DomainError.toMessage(): UiText = UiText(Res.string.rf_error_save)

    private fun RefuelConfirmUiState.fromDraft(draft: FuelFillDraft) = copy(
        source = draft.source,
        stationName = draft.stationName,
        amount = rupeeText(draft.amount?.paise),
        amountOrigin = draft.amountOrigin,
        rate = rupeeText(draft.pricePerUnit?.paise),
        rateOrigin = draft.priceOrigin,
        quantity = unitText(draft.quantityMilli),
        quantityOrigin = draft.quantityOrigin,
        odometerKm = draft.odometerKm?.toLong(),
        odometerOrigin = draft.odometerOrigin,
        unitLabel = draft.unit.label(),
    )

    private fun FuelUnit.label(): UiText = when (this) {
        FuelUnit.LITRE -> UiText(Res.string.rf_unit_litre)
        FuelUnit.KILOGRAM -> UiText(Res.string.rf_unit_kg)
        FuelUnit.KILOWATT_HOUR -> UiText(Res.string.rf_unit_kwh)
    }

    private companion object {
        const val OP_LOAD = "refuel_confirm_load"
        const val OP_CONFIRM = "refuel_confirm_save"
        const val OP_REJECT = "refuel_confirm_reject"
        const val UNKNOWN = "Unknown"
    }
}

/**
 * Drop whatever was calculated rather than observed, so it can be worked out again.
 *
 * Without this, editing the amount would leave the old derived quantity in place and
 * [FuelFillDraft.completed] would find nothing to do — the screen would show a quantity that
 * no longer matches what the owner just typed.
 */
private fun FuelFillDraft.clearDerived(): FuelFillDraft = copy(
    amount = if (amountOrigin == FieldOrigin.DERIVED) null else amount,
    quantityMilli = if (quantityOrigin == FieldOrigin.DERIVED) null else quantityMilli,
    pricePerUnit = if (priceOrigin == FieldOrigin.DERIVED) null else pricePerUnit,
)

/** How many of the four fields arrived filled in — the measure of what a channel saved. */
private fun FuelFillDraft.knownFieldCount(): Int = listOf(
    amount != null,
    quantityMilli != null,
    pricePerUnit != null,
    odometerKm != null,
).count { it }
