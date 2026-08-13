package com.hopcape.odo.feature.documentvault.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.platform.file.PlatformDownloads
import com.hopcape.odo.core.platform.file.StoredFileKinds
import com.hopcape.odo.feature.documentvault.domain.usecase.DeleteDocumentUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.DocumentDetail
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ReplaceDocumentFileUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.exportFileName
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
    private val downloads: PlatformDownloads,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    private val submission = MutableStateFlow<Submission>(Submission.Idle)

    /** The document as it was last read — what the file actions act on. */
    private var document: Document? = null

    private val _effects = Channel<DocumentDetailEffect>(Channel.BUFFERED)
    val effects: Flow<DocumentDetailEffect> = _effects.receiveAsFlow()

    private var reportedOpen = false

    /**
     * Set once the screen has asked to leave.
     *
     * Deleting gives it two reasons to leave at almost the same moment: the delete itself
     * succeeds, and the document then stops arriving. Both are right, and both used to pop —
     * which took the vault behind this screen with it and left the owner in the garage.
     */
    private var leaving = false

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
        DocumentDetailEvent.File.View -> view()
        DocumentDetailEvent.File.Download -> saveCopy()
        is DocumentDetailEvent.File.Replace -> replace(event.pickedRef)
        DocumentDetailEvent.File.Delete -> delete()
    }

    /**
     * Put a copy of the file where the owner keeps their downloads.
     *
     * The outcome is reported either way. Nothing opens and nothing on this screen changes,
     * so a save that says nothing is indistinguishable from a menu item that does nothing —
     * which is what this one used to be.
     */
    private fun saveCopy() {
        val document = document ?: return
        viewModelScope.launch {
            downloads.saveCopy(
                storageKey = document.storagePath,
                fileName = document.exportFileName(),
                mimeType = StoredFileKinds.mimeTypeOf(document.storagePath),
            ).fold(
                ifLeft = { emit(DocumentDetailEffect.CopySaveFailed) },
                ifRight = {
                    telemetry.documentShared(document.type, DOWNLOADS_TARGET)
                    emit(DocumentDetailEffect.CopySaved)
                },
            )
        }
    }

    /** Open the stored file in the shared viewer. Nothing to open until the document loads. */
    private fun view() {
        val content = current() ?: return
        val path = content.storagePath ?: return
        telemetry.documentPreviewed(content.type)
        emit(DocumentDetailEffect.OpenFile(path))
    }

    /** The document on screen, or null while it is still loading. */
    private fun current(): DocumentDetailContent? = (state.value.content as? Loadable.Ready)?.value

    private fun onOpenEvent(event: DocumentDetailEvent.Open) = when (event) {
        DocumentDetailEvent.Open.Share -> emit(DocumentDetailEffect.OpenShare(documentId))
        DocumentDetailEvent.Open.Renew -> renew()
        DocumentDetailEvent.Open.EditDates -> emit(DocumentDetailEffect.OpenEditDates(documentId))
        DocumentDetailEvent.Open.Back -> leave()
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
                    leave()
                },
            )
        }
    }

    private fun renew() {
        val type = current()?.type ?: return
        emit(DocumentDetailEffect.OpenAdd(type))
    }

    /** Leave the screen, at most once — see [leaving]. */
    private fun leave() {
        if (leaving) return
        leaving = true
        emit(DocumentDetailEffect.NavigateBack)
    }

    private fun emit(effect: DocumentDetailEffect) {
        _effects.trySend(effect)
        Unit
    }

    /** Report the open, once, from the first emission that carries a document. */
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
            leave()
            return Loadable.Loading
        }
        document = detail.document
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
                storagePath = detail.document.storagePath,
            ),
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** Recorded like any other way a document leaves the app. */
        const val DOWNLOADS_TARGET = "DOWNLOADS"
    }
}
