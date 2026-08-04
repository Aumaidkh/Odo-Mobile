package com.hopcape.odo.feature.billscanner.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.servicelog.analysis.ServiceCategoryGuesser
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.navigation.FairnessLineInput
import com.hopcape.odo.feature.billscanner.domain.usecase.ReviewedLineItem
import com.hopcape.odo.feature.billscanner.domain.usecase.SaveScannedBillCommand
import com.hopcape.odo.feature.billscanner.domain.usecase.SaveScannedBillUseCase
import com.hopcape.odo.feature.billscanner.domain.usecase.ScanBillUseCase
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * State holder for the review step: reads the photo, shows what came back, and writes what the
 * owner confirmed.
 *
 * The extraction runs **here** rather than on the scan screen, keyed by the photo's storage
 * key. That keeps the navigation key to one string, and it means backing out and returning
 * re-reads the same photo instead of needing the result carried through the back stack. A
 * repeat read costs nothing on the server, which caches by image hash (TDD §7.5).
 *
 * The three header fields are editable and start from what was read. Everything the owner
 * touches is tracked in [edited], because "how often does the extraction need correcting" is
 * the number that tells us whether the scanner is actually working.
 */
internal class BillReviewViewModel(
    private val photoKey: String?,
    private val scanBill: ScanBillUseCase,
    private val saveBill: SaveScannedBillUseCase,
    private val activeCar: ActiveCarProvider,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: BillScannerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(BillReviewUiState(photoKey = photoKey))
    val state: StateFlow<BillReviewUiState> = _state.asStateFlow()

    private val _effects = Channel<BillReviewEffect>(Channel.BUFFERED)
    val effects: Flow<BillReviewEffect> = _effects.receiveAsFlow()

    /** What the extractor read, kept so the saved entry can name the scan it came from. */
    private var extracted: ExtractedBill? = null
    private var edited = false

    init {
        read()
    }

    fun onEvent(event: BillReviewEvent) = when (event) {
        is BillReviewEvent.WorkshopChanged -> edit { it.copy(workshop = event.value) }
        is BillReviewEvent.DateChanged -> edit { it.copy(serviceDate = event.value) }
        is BillReviewEvent.OdometerChanged -> edit { it.copy(odometer = event.value) }
        BillReviewEvent.SaveTapped -> save()
        BillReviewEvent.RetakeTapped -> emit(BillReviewEffect.Retake)
        BillReviewEvent.BackTapped -> emit(BillReviewEffect.NavigateBack)
    }

    /**
     * Read the photo.
     *
     * With no photo key there is nothing to read — the flow was entered from somewhere that
     * had no capture — so the screen says so rather than showing a spinner that never ends.
     */
    private fun read() {
        val key = photoKey
        if (key == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.bs_error_no_car))) }
            return
        }
        viewModelScope.launch(telemetry.op(OP_READ)) {
            _state.update { it.copy(submission = Submission.InFlight) }
            telemetry.billExtraction { scanBill(key) }.fold(
                ifLeft = { error -> _state.update { it.copy(submission = error.toSubmissionFailure()) } },
                ifRight = ::show,
            )
        }
    }

    /** Fill the form from what was read. Nothing is invented: a field that was not read stays empty. */
    private fun show(bill: ExtractedBill) {
        extracted = bill
        _state.update { current ->
            current.copy(
                submission = Submission.Idle,
                confidence = bill.confidence.percent,
                requiresReview = bill.requiresManualReview,
                workshop = bill.workshopName.orEmpty(),
                serviceDate = bill.serviceDate,
                odometer = bill.odometerKm?.toString().orEmpty(),
                lineItems = bill.lineItems.map { line ->
                    BillLineItem(
                        label = line.label,
                        amount = line.amount,
                        needsCheck = line.needsCheck,
                    )
                },
                total = bill.total ?: Amount.ZERO,
            )
        }
    }

    private fun edit(change: (BillReviewUiState) -> BillReviewUiState) {
        edited = true
        _state.update { change(it).copy(submission = Submission.Idle) }
    }

    /**
     * Write the entry, then hand its lines to the fairness check.
     *
     * The fairness step runs only after the write succeeded: a report about an entry that was
     * never saved has nothing to offer "report overcharge" against.
     */
    private fun save() {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.bs_error_no_car))) }
            return
        }
        val current = _state.value
        viewModelScope.launch(telemetry.op(OP_SAVE)) {
            _state.update { it.copy(submission = Submission.InFlight) }
            val command = SaveScannedBillCommand(
                serviceDate = current.serviceDate,
                odometerKm = current.odometer.toIntOrNull(),
                totalPaise = current.total.paise,
                workshopName = current.workshop.ifBlank { null },
                lineItems = current.lineItems.map { ReviewedLineItem(it.label, it.amount.paise) },
                photoStorageKey = photoKey,
                scanId = extracted?.scanId?.value,
            )
            telemetry.billSave(edited) {
                saveBill(command, carId, currentOwner.currentOwnerId())
            }.fold(
                ifLeft = { errors ->
                    _state.update { it.copy(submission = errors.toList().toSubmissionFailure()) }
                },
                ifRight = { entry ->
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    emit(
                        BillReviewEffect.OpenFairness(
                            items = current.lineItems.map { line ->
                                FairnessLineInput(
                                    label = line.label,
                                    // A line the guesser does not recognise travels
                                    // uncategorised, and the report carries it through
                                    // unjudged rather than benchmarking it against the
                                    // wrong job.
                                    category = ServiceCategoryGuesser.of(line.label)?.name,
                                    amountPaise = line.amount.paise,
                                )
                            },
                            logId = entry.id.value,
                            carId = carId.value,
                        ),
                    )
                },
            )
        }
    }

    private fun emit(effect: BillReviewEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val OP_READ = "review_read"
        const val OP_SAVE = "review_save"
    }
}
