package com.hopcape.odo.feature.billscanner.presentation.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.feature.billscanner.domain.usecase.CaptureOrigin
import com.hopcape.odo.feature.billscanner.domain.usecase.SaveScannedDocumentCommand
import com.hopcape.odo.feature.billscanner.domain.usecase.SaveScannedDocumentUseCase
import com.hopcape.odo.feature.billscanner.domain.usecase.ScanDocumentUseCase
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

/**
 * State holder for the scanned-document confirm step.
 *
 * Same shape as the bill review: the file is read here, keyed by its storage key, and what
 * the owner confirms is what gets filed. The difference is what the screen insists on — for
 * a paper that renews, a document with no expiry date is not saveable, because the reminder
 * it exists to produce would never fire.
 *
 * Both ways of adding a document end here. A photographed paper arrives from the camera and
 * an uploaded one from the vault's file picker; [origin] is the only difference, and it only
 * decides what the row records about where the file came from.
 */
internal class DocumentReviewViewModel(
    private val photoKey: String?,
    private val initialType: DocumentType?,
    private val origin: CaptureOrigin,
    private val scanDocument: ScanDocumentUseCase,
    private val saveDocument: SaveScannedDocumentUseCase,
    private val activeCar: ActiveCarProvider,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: BillScannerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DocumentReviewUiState(
            photoKey = photoKey,
            type = initialType ?: DocumentType.INSURANCE,
        ),
    )
    val state: StateFlow<DocumentReviewUiState> = _state.asStateFlow()

    private val _effects = Channel<DocumentReviewEffect>(Channel.BUFFERED)
    val effects: Flow<DocumentReviewEffect> = _effects.receiveAsFlow()

    init {
        read()
    }

    fun onEvent(event: DocumentReviewEvent) = when (event) {
        is DocumentReviewEvent.TypeChanged -> edit { it.copy(type = event.type) }
        is DocumentReviewEvent.TitleChanged -> edit { it.copy(title = event.value) }
        is DocumentReviewEvent.IssuedOnChanged -> edit { it.copy(issuedOn = event.value) }
        is DocumentReviewEvent.ExpiresOnChanged -> edit { it.copy(expiresOn = event.value) }
        DocumentReviewEvent.SaveTapped -> save()
        DocumentReviewEvent.BackTapped -> emit(DocumentReviewEffect.NavigateBack)
    }

    private fun read() {
        val key = photoKey
        if (key == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.bs_error_no_car))) }
            return
        }
        viewModelScope.launch(telemetry.op(OP_READ)) {
            _state.update { it.copy(submission = Submission.InFlight) }
            telemetry.documentExtraction(
                read = { scanDocument(key) },
                hasExpiry = { it.expiresOn != null },
            ).fold(
                ifLeft = { error ->
                    // A failed read is not a dead end here: the owner can still type the
                    // dates off the paper in front of them, so the form stays usable.
                    _state.update { it.copy(submission = error.toSubmissionFailure()) }
                },
                ifRight = ::show,
            )
        }
    }

    private fun show(document: ExtractedDocument) {
        _state.update { current ->
            current.copy(
                submission = Submission.Idle,
                // The caller's type wins over the read's guess: the owner tapped "Add" on the
                // RC row, so an insurance-looking policy in frame does not get to rename it.
                type = initialType ?: document.documentType ?: current.type,
                title = document.suggestedTitle.orEmpty(),
                issuedOn = document.issuedOn,
                expiresOn = document.expiresOn,
            )
        }
    }

    private fun edit(change: (DocumentReviewUiState) -> DocumentReviewUiState) {
        _state.update { change(it).copy(submission = Submission.Idle) }
    }

    private fun save() {
        val carId = activeCar.activeCarId.value
        val key = photoKey
        if (carId == null || key == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.bs_error_no_car))) }
            return
        }
        val current = _state.value
        viewModelScope.launch(telemetry.op(OP_SAVE)) {
            _state.update { it.copy(submission = Submission.InFlight) }
            val command = SaveScannedDocumentCommand(
                type = current.type,
                photoStorageKey = key,
                origin = origin,
                title = current.title.ifBlank { null },
                issuedOn = current.issuedOn,
                expiresOn = current.expiresOn,
            )
            telemetry.documentSave(current.type.name, origin.name) {
                saveDocument(command, carId, currentOwner.currentOwnerId())
            }.fold(
                ifLeft = { errors ->
                    _state.update { it.copy(submission = errors.toList().toSubmissionFailure()) }
                },
                ifRight = { document ->
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    emit(DocumentReviewEffect.OpenDocument(document.id.value))
                },
            )
        }
    }

    private fun emit(effect: DocumentReviewEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val OP_READ = "document_read"
        const val OP_SAVE = "document_save"
    }
}
