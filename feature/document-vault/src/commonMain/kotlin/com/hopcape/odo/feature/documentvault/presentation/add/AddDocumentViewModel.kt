package com.hopcape.odo.feature.documentvault.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.feature.documentvault.domain.usecase.StageUploadedDocumentUseCase
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import com.hopcape.odo.feature.documentvault.presentation.state.Submission
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.resources.dv_error_capture_unavailable
import com.hopcape.odo.feature.documentvault.resources.dv_error_limit_reached
import com.hopcape.odo.feature.documentvault.resources.dv_error_no_car
import com.hopcape.odo.feature.documentvault.resources.dv_error_write_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the add-document flow.
 *
 * The type the flow opens on is passed by the route from its navigation key. A vault row's
 * "Add" and a document's "Renew now" both land here with their type already chosen; opening
 * the flow with no type at all is also allowed, and starts on insurance.
 *
 * Neither capture path files anything here. Both the camera and the file picker end at the
 * confirm step, which reads the paper's dates and writes the row — a document filed without
 * an expiry produces no reminder, which is most of what the vault is for. DigiLocker has no
 * capture behind it, so it says so rather than pretending.
 */
internal class AddDocumentViewModel(
    private val prefillType: DocumentType?,
    private val stageDocument: StageUploadedDocumentUseCase,
    private val activeCar: ActiveCarProvider,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AddDocumentUiState(selectedType = prefillType ?: DocumentType.INSURANCE),
    )
    val state: StateFlow<AddDocumentUiState> = _state.asStateFlow()

    private val _effects = Channel<AddDocumentEffect>(Channel.BUFFERED)
    val effects: Flow<AddDocumentEffect> = _effects.receiveAsFlow()

    init {
        telemetry.addOpened(prefilled = prefillType != null)
    }

    fun onEvent(event: AddDocumentEvent) = when (event) {
        is AddDocumentEvent.TypeSelected -> selectType(event.type)
        is AddDocumentEvent.Capture -> onCapture(event)
        AddDocumentEvent.CloseTapped -> emit(AddDocumentEffect.NavigateBack)
    }

    private fun selectType(type: DocumentType) {
        telemetry.typeSelected(type)
        _state.update { it.copy(selectedType = type, submission = Submission.Idle) }
    }

    private fun onCapture(event: AddDocumentEvent.Capture) = when (event) {
        AddDocumentEvent.Capture.Scan -> scan()
        AddDocumentEvent.Capture.DigiLocker -> captureUnavailable(DocumentVaultTelemetry.CaptureMethod.DIGILOCKER)
        // A cancelled picker is not a failure; the screen stays as it was.
        is AddDocumentEvent.Capture.FilePicked -> event.pickedRef?.let(::stage) ?: Unit
    }

    /**
     * Hand the chosen type to the scanner and let it read the paper.
     *
     * Nothing is saved here: the scanner captures the photo and its confirm step files the
     * document, which is also the only path that can fill in an expiry date.
     */
    private fun scan() {
        val type = _state.value.selectedType
        telemetry.captureStarted(DocumentVaultTelemetry.CaptureMethod.SCAN, type)
        emit(AddDocumentEffect.OpenScanner(type))
    }

    private fun captureUnavailable(method: String) {
        telemetry.captureUnavailable(method)
        _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.dv_error_capture_unavailable))) }
    }

    /**
     * Copy the picked file into app storage, then hand it to the confirm step.
     *
     * Nothing is filed here. The copy is what makes the file readable at all — a picker's
     * handle stops working once the process dies — and the step after it reads the dates off
     * the paper and writes the row.
     */
    private fun stage(pickedRef: String) {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.dv_error_no_car))) }
            return
        }

        val type = _state.value.selectedType
        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(DocumentVaultTelemetry.Trace.STAGE_FILE)) {
            telemetry.documentStage(type) {
                stageDocument(
                    pickedRef = pickedRef,
                    carId = carId,
                    ownerId = currentOwner.currentOwnerId(),
                )
            }.fold(
                ifLeft = { error -> _state.update { it.copy(submission = Submission.Failed(error.toMessage())) } },
                ifRight = { storageKey ->
                    _state.update { it.copy(submission = Submission.Idle) }
                    emit(AddDocumentEffect.OpenReview(storageKey = storageKey, type = type))
                },
            )
        }
    }

    private fun emit(effect: AddDocumentEffect) {
        _effects.trySend(effect)
        Unit
    }

}

/**
 * The message a failed add shows. A full plan gets its own message naming the cap — the
 * generic "something went wrong" read as a broken app, and hid the owner's next step.
 * Every other failure stays generic: the owner cannot act on a storage error's details.
 */
private fun DomainError.toMessage(): UiText =
    if (this is DomainError.DocumentLimitReached) UiText(Res.string.dv_error_limit_reached, listOf(limit))
    else UiText(Res.string.dv_error_write_failed)
