package com.hopcape.odo.feature.billscanner.presentation.pay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.payment.UpiDeepLink
import com.hopcape.odo.core.domain.payment.UpiQrParser
import com.hopcape.odo.core.domain.payment.model.UpiPaymentResult
import com.hopcape.odo.core.domain.payment.model.UpiPaymentStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.billscanner.domain.usecase.LogFuelFillCommand
import com.hopcape.odo.feature.billscanner.domain.usecase.LogFuelFillUseCase
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTelemetry
import com.hopcape.odo.feature.billscanner.presentation.state.Submission
import com.hopcape.odo.feature.billscanner.presentation.toSubmissionFailure
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_error_no_car
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * State holder for scan-to-pay: parse the code, pay it, then record what it bought.
 *
 * The rule that shapes everything here is that **only a confirmed payment produces a fill**.
 * A UPI hand-off can come back pending, and a fill written against a payment that later fails
 * is a fabricated record in a history the app promises is trustworthy. Pending therefore stops
 * the flow with an explanation rather than sliding on to the logging stage.
 *
 * The amount is carried from the payment rather than re-read from the code, because most
 * pump QRs name no sum and the figure that belongs on the record is the one that left the
 * owner's account.
 */
internal class PayAtPumpViewModel(
    private val payload: String,
    private val logFill: LogFuelFillUseCase,
    private val cars: CarRepository,
    private val activeCar: ActiveCarProvider,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: BillScannerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(PayAtPumpUiState())
    val state: StateFlow<PayAtPumpUiState> = _state.asStateFlow()

    private val _effects = Channel<PayAtPumpEffect>(Channel.BUFFERED)
    val effects: Flow<PayAtPumpEffect> = _effects.receiveAsFlow()

    /** The confirmed payment, kept so the fill it produces can carry its bank reference. */
    private var payment: UpiPaymentResult? = null

    init {
        parse()
    }

    fun onEvent(event: PayAtPumpEvent) = when (event) {
        is PayAtPumpEvent.AmountChanged -> edit { it.copy(amount = event.value) }
        is PayAtPumpEvent.OdometerChanged -> edit { it.copy(odometer = event.value) }
        is PayAtPumpEvent.LitresChanged -> edit { it.copy(litres = event.value) }
        is PayAtPumpEvent.StationChanged -> edit { it.copy(station = event.value) }
        PayAtPumpEvent.PayTapped -> pay()
        is PayAtPumpEvent.PaymentReturned -> paymentReturned(event.response)
        is PayAtPumpEvent.PaymentUnavailable -> paymentUnavailable(event.onThisPlatform)
        PayAtPumpEvent.SaveFillTapped -> saveFill()
        PayAtPumpEvent.BackTapped -> emit(PayAtPumpEffect.NavigateBack)
    }

    /** Read the code. A prefilled amount is offered as text so the owner can still change it. */
    private fun parse() {
        UpiQrParser.parse(payload).fold(
            ifLeft = { error ->
                telemetry.paymentQrRejected(error::class.simpleName.orEmpty())
                _state.update { it.copy(submission = error.toSubmissionFailure()) }
            },
            ifRight = { request ->
                telemetry.paymentQrParsed(request.hasAmount)
                _state.update {
                    it.copy(
                        request = request,
                        amount = request.amount?.let(::rupees).orEmpty(),
                        submission = Submission.Idle,
                    )
                }
            },
        )
    }

    private fun edit(change: (PayAtPumpUiState) -> PayAtPumpUiState) {
        _state.update { change(it).copy(submission = Submission.Idle) }
    }

    private fun pay() {
        val current = _state.value
        val request = current.request ?: return
        val paise = current.amountPaise ?: return
        val amount = Amount.of(paise).getOrNull() ?: return

        telemetry.paymentInitiated()
        _state.update { it.copy(submission = Submission.InFlight) }
        emit(PayAtPumpEffect.LaunchUpi(UpiDeepLink.candidates(request, amount, reference())))
    }

    /**
     * The UPI app came back.
     *
     * Only [UpiPaymentStatus.Success] moves the screen on. Pending is reported and the flow
     * stops here — the owner is told to check their bank app, because Odo cannot know whether
     * the money moved and must not claim either way.
     */
    private fun paymentReturned(response: String?) {
        val result = UpiDeepLink.parseResponse(response)
        telemetry.paymentSettled(result.status.name, result.transactionRef ?: result.transactionId)

        when (result.status) {
            UpiPaymentStatus.Success -> {
                payment = result
                _state.update { it.copy(stage = PayStage.Logging, submission = Submission.Idle) }
            }
            UpiPaymentStatus.Pending -> _state.update {
                it.copy(submission = DomainError.PaymentPending.toSubmissionFailure())
            }
            UpiPaymentStatus.Failed -> _state.update {
                it.copy(submission = DomainError.PaymentCancelled.toSubmissionFailure())
            }
        }
    }

    private fun paymentUnavailable(onThisPlatform: Boolean) {
        val error = if (onThisPlatform) {
            DomainError.UpiUnsupportedOnDevice
        } else {
            DomainError.NoUpiAppAvailable
        }
        _state.update { it.copy(submission = error.toSubmissionFailure()) }
    }

    /** Write the fill. Reached only from the logging stage, which only a success opens. */
    private fun saveFill() {
        val carId = activeCar.activeCarId.value
        val confirmed = payment
        if (carId == null || confirmed == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.bs_error_no_car))) }
            return
        }
        val current = _state.value
        val paise = current.amountPaise ?: return
        val amount = Amount.of(paise).getOrNull() ?: return

        viewModelScope.launch(telemetry.op(OP_SAVE_FILL)) {
            _state.update { it.copy(submission = Submission.InFlight) }
            val command = LogFuelFillCommand(
                payment = confirmed,
                amount = amount,
                odometerKm = current.odometer.toIntOrNull(),
                quantityMilli = current.quantityMilli,
                unit = fuelUnit(carId),
                stationName = current.station.ifBlank { current.request?.payeeName },
            )
            telemetry.fillSave { logFill(command, carId, currentOwner.currentOwnerId()) }.fold(
                ifLeft = { errors ->
                    _state.update { it.copy(submission = errors.toList().toSubmissionFailure()) }
                },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    emit(PayAtPumpEffect.FillSaved)
                },
            )
        }
    }

    /**
     * The unit the car's fuel is sold by — litres for petrol and diesel, kilograms for CNG.
     *
     * Read from the car rather than assumed, because storing kilograms of CNG as litres would
     * make every mileage figure derived from it wrong. Falls back to litres only when the car
     * cannot be read at all, which is the majority case and the least damaging guess.
     */
    private suspend fun fuelUnit(carId: CarId): FuelUnit =
        cars.observe(carId).first()?.let { car: Car -> FuelUnit.of(car.fuelType) } ?: FuelUnit.LITRE

    /**
     * A reference for this attempt, unique because the clock has moved.
     *
     * Read straight from the clock rather than injected: it is not a decision the app makes,
     * nothing branches on it, and the only property that matters — that tapping Pay twice
     * produces two different values — holds for any two taps a human can perform. A reused
     * reference reads to the payment network as a duplicate and is refused.
     */
    private fun reference(): String = "ODO${Clock.System.now().toEpochMilliseconds()}"

    private fun rupees(amount: Amount): String {
        val whole = amount.paise / 100
        val fraction = (amount.paise % 100).toString().padStart(2, '0')
        return "$whole.$fraction"
    }

    private fun emit(effect: PayAtPumpEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val OP_SAVE_FILL = "pay_save_fill"
    }
}
