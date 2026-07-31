package com.hopcape.odo.feature.documentvault.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.feature.documentvault.domain.usecase.AddDocumentCommand
import com.hopcape.odo.feature.documentvault.domain.usecase.AddDocumentUseCase
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import com.hopcape.odo.feature.documentvault.presentation.state.Submission
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_error_capture_unavailable
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
 * Only the upload path saves anything today. Scanning and DigiLocker have no capture behind
 * them, so they say so instead of walking the owner to a success screen for a document that
 * was never written.
 */
internal class AddDocumentViewModel(
    private val prefillType: DocumentType?,
    private val addDocument: AddDocumentUseCase,
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
        AddDocumentEvent.Capture.Scan -> captureUnavailable(DocumentVaultTelemetry.CaptureMethod.SCAN)
        AddDocumentEvent.Capture.DigiLocker -> captureUnavailable(DocumentVaultTelemetry.CaptureMethod.DIGILOCKER)
        // A cancelled picker is not a failure; the screen stays as it was.
        is AddDocumentEvent.Capture.FilePicked -> event.pickedRef?.let(::save) ?: Unit
    }

    private fun captureUnavailable(method: String) {
        telemetry.captureUnavailable(method)
        _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.dv_error_capture_unavailable))) }
    }

    /**
     * Save the picked file as a document of the selected type.
     *
     * Dates are not collected here: this screen has no fields for them. Until a
     * review-and-confirm step exists, a document is stored with what is actually known —
     * its type and its file — and the expiry stays absent rather than guessed.
     */
    private fun save(pickedRef: String) {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.dv_error_no_car))) }
            return
        }

        val type = _state.value.selectedType
        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(DocumentVaultTelemetry.Trace.SAVE_DOCUMENT)) {
            telemetry.documentSave(type) {
                addDocument(
                    command = AddDocumentCommand(
                        type = type,
                        pickedRef = pickedRef,
                        source = DocumentSource.UPLOADED,
                    ),
                    carId = carId,
                    ownerId = currentOwner.currentOwnerId(),
                )
            }.fold(
                ifLeft = { _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.dv_error_write_failed))) } },
                ifRight = { document ->
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    emit(AddDocumentEffect.OpenSuccess(document.id))
                },
            )
        }
    }

    private fun emit(effect: AddDocumentEffect) {
        _effects.trySend(effect)
        Unit
    }

}
