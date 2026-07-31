package com.hopcape.odo.feature.documentvault.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.feature.documentvault.domain.usecase.DeleteDocumentUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.DocumentDetail
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ReplaceDocumentFileUseCase
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import com.hopcape.odo.feature.documentvault.presentation.state.Submission
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_error_load_failed
import com.hopcape.odo.feature.documentvault.resources.dv_error_write_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for one document's detail.
 *
 * The document id is a constructor argument, passed by the route from its navigation key.
 * The key is what the back stack stores and restores, so the id survives process death with
 * the entry that carries it.
 */
internal class DocumentDetailViewModel(
    private val documentId: DocumentId,
    observeDetail: ObserveDocumentDetailUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val replaceFile: ReplaceDocumentFileUseCase,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    private val submission = MutableStateFlow<Submission>(Submission.Idle)

    private val _effects = Channel<DocumentDetailEffect>(Channel.BUFFERED)
    val effects: Flow<DocumentDetailEffect> = _effects.receiveAsFlow()

    /** The storage path of what is on screen, for the actions that act on the file. */
    private var storagePath: String? = null
    private var reportedOpen = false

    val state: StateFlow<DocumentDetailUiState> =
        combine(
            observeDetail(documentId).map(::toContent).onEach(::remember),
            submission,
        ) { content, submission ->
            DocumentDetailUiState(content = content, submission = submission)
        }
            .catch { cause ->
                telemetry.readFailed(DocumentVaultTelemetry.Screen.DETAIL, cause)
                emit(DocumentDetailUiState(content = Loadable.Failed(UiText(Res.string.dv_error_load_failed))))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = DocumentDetailUiState(),
            )

    fun onEvent(event: DocumentDetailEvent) = when (event) {
        is DocumentDetailEvent.File -> onFileEvent(event)
        is DocumentDetailEvent.Open -> onOpenEvent(event)
    }

    private fun onFileEvent(event: DocumentDetailEvent.File) = when (event) {
        DocumentDetailEvent.File.View -> storagePath?.let { emit(DocumentDetailEffect.OpenFile(it)) } ?: Unit
        DocumentDetailEvent.File.Download -> storagePath?.let { emit(DocumentDetailEffect.DownloadFile(it)) } ?: Unit
        is DocumentDetailEvent.File.Replace -> replace(event.pickedRef)
        DocumentDetailEvent.File.Delete -> delete()
    }

    private fun onOpenEvent(event: DocumentDetailEvent.Open) = when (event) {
        DocumentDetailEvent.Open.Share -> emit(DocumentDetailEffect.OpenShare(documentId))
        DocumentDetailEvent.Open.Renew -> renew()
        DocumentDetailEvent.Open.Back -> emit(DocumentDetailEffect.NavigateBack)
    }

    /**
     * Swap the stored file. The picked file always counts as owner-supplied, so a
     * replacement drops a DigiLocker copy's Verified badge — which is the truth about
     * where the new bytes came from.
     */
    private fun replace(pickedRef: String) {
        submission.update { Submission.InFlight }
        viewModelScope.launch(telemetry.op(DocumentVaultTelemetry.Trace.REPLACE_FILE)) {
            telemetry.fileReplace(documentId) {
                replaceFile(documentId, pickedRef, DocumentSource.UPLOADED)
            }.fold(
                ifLeft = { submission.update { Submission.Failed(UiText(Res.string.dv_error_write_failed)) } },
                ifRight = { submission.update { Submission.Idle } },
            )
        }
    }

    /** Delete, then leave: the screen has nothing left to show. */
    private fun delete() {
        submission.update { Submission.InFlight }
        viewModelScope.launch(telemetry.op(DocumentVaultTelemetry.Trace.DELETE_DOCUMENT)) {
            telemetry.documentDelete(documentId) { deleteDocument(documentId) }.fold(
                ifLeft = { submission.update { Submission.Failed(UiText(Res.string.dv_error_write_failed)) } },
                ifRight = {
                    submission.update { Submission.Succeeded }
                    emit(DocumentDetailEffect.NavigateBack)
                },
            )
        }
    }

    private fun renew() {
        val type = (state.value.content as? Loadable.Ready)?.value?.type ?: return
        emit(DocumentDetailEffect.OpenAdd(type))
    }

    private fun emit(effect: DocumentDetailEffect) {
        _effects.trySend(effect)
        Unit
    }

    /** Keep the file path and report the open, both from the same emission. */
    private fun remember(content: Loadable<DocumentDetailContent>) {
        val detail = (content as? Loadable.Ready)?.value ?: return
        if (reportedOpen) return
        reportedOpen = true
        telemetry.documentOpened(type = detail.type, verified = detail.isVerified)
        if (!detail.isFileAvailable) telemetry.fileMissing(documentId)
    }

    private fun toContent(detail: DocumentDetail?): Loadable<DocumentDetailContent> {
        if (detail == null) {
            telemetry.documentMissing(documentId)
            emit(DocumentDetailEffect.NavigateBack)
            return Loadable.Loading
        }
        storagePath = detail.document.storagePath
        return Loadable.Ready(
            DocumentDetailContent(
                id = detail.document.id,
                type = detail.document.type,
                title = detail.document.title?.value,
                validity = detail.validity,
                issuedOn = detail.document.issuedOn,
                validityProgress = detail.validityProgress,
                reminderDaysBefore = detail.nextReminder?.daysBefore,
                isVerified = detail.isVerified,
                isFileAvailable = detail.isFileAvailable,
            ),
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
