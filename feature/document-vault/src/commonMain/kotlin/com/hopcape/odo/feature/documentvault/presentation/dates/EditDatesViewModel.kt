package com.hopcape.odo.feature.documentvault.presentation.dates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.UpdateDocumentCommand
import com.hopcape.odo.feature.documentvault.domain.usecase.UpdateDocumentUseCase
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import com.hopcape.odo.feature.documentvault.presentation.state.Submission
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_error_write_failed
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
 * State holder for the sheet that corrects a document's dates.
 *
 * The document is read once, not observed: this is a form, and a row changing underneath it
 * would overwrite what the owner is halfway through typing. The title and the type are left
 * exactly as they were — this sheet is about the dates, and passing the existing values back
 * unchanged is what keeps it from quietly editing anything else.
 */
internal class EditDatesViewModel(
    private val documentId: DocumentId,
    private val observeDetail: ObserveDocumentDetailUseCase,
    private val updateDocument: UpdateDocumentUseCase,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(EditDatesUiState(type = DocumentType.INSURANCE))
    val state: StateFlow<EditDatesUiState> = _state.asStateFlow()

    private val _effects = Channel<EditDatesEffect>(Channel.BUFFERED)
    val effects: Flow<EditDatesEffect> = _effects.receiveAsFlow()

    /** The document as it stands, so a save keeps everything the sheet does not edit. */
    private var current: Document? = null

    init {
        load()
    }

    fun onEvent(event: EditDatesEvent) = when (event) {
        is EditDatesEvent.IssuedOnChanged -> edit { it.copy(issuedOn = event.value) }
        is EditDatesEvent.ExpiresOnChanged -> edit { it.copy(expiresOn = event.value) }
        EditDatesEvent.SaveTapped -> save()
    }

    private fun load() {
        viewModelScope.launch {
            val detail = runCatching { observeDetail(documentId).first() }
                .onFailure { cause -> telemetry.readFailed(DocumentVaultTelemetry.Screen.EDIT_DATES, cause) }
                .getOrNull()
            val document = detail?.document ?: return@launch
            current = document
            _state.update {
                it.copy(
                    type = document.type,
                    issuedOn = document.issuedOn,
                    expiresOn = document.expiresOn,
                )
            }
        }
    }

    private fun edit(change: (EditDatesUiState) -> EditDatesUiState) {
        _state.update { change(it).copy(submission = Submission.Idle) }
    }

    private fun save() {
        val document = current ?: return
        val edited = _state.value
        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(DocumentVaultTelemetry.Trace.EDIT_DATES)) {
            telemetry.datesEdited(document.type, hasExpiry = edited.expiresOn != null) {
                updateDocument(
                    id = document.id,
                    command = UpdateDocumentCommand(
                        type = document.type,
                        title = document.title?.value,
                        issuedOn = edited.issuedOn,
                        expiresOn = edited.expiresOn,
                    ),
                )
            }.fold(
                ifLeft = {
                    _state.update {
                        it.copy(submission = Submission.Failed(UiText(Res.string.dv_error_write_failed)))
                    }
                },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    _effects.trySend(EditDatesEffect.Dismiss)
                },
            )
        }
    }
}
