package com.hopcape.odo.feature.documentvault.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.platform.file.PlatformDownloads
import com.hopcape.odo.core.platform.file.StoredFileKinds
import com.hopcape.odo.feature.documentvault.domain.usecase.DocumentDetail
import com.hopcape.odo.feature.documentvault.domain.usecase.ExportDocumentFileUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.exportFileName
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_share_failed
import com.hopcape.odo.feature.documentvault.resources.dv_share_save_failed
import com.hopcape.odo.feature.documentvault.resources.dv_share_saved
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the share sheet. Reads the document being shared, and acts on its file
 * when the owner picks one of the two things that can be done with it.
 *
 * The sheet emits `null` state until the document loads; a sheet that opens on nothing is
 * better than one that offers to share a document it has not read.
 *
 * Sharing exports a copy first ([ExportDocumentFileUseCase]) — the vault's own directory is
 * not readable by other apps. Saving a copy does not: it writes the file out through the
 * platform rather than handing it to anyone.
 */
internal class ShareDocumentViewModel(
    documentId: DocumentId,
    observeDetail: ObserveDocumentDetailUseCase,
    private val exportFile: ExportDocumentFileUseCase,
    private val downloads: PlatformDownloads,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    private var document: Document? = null

    private val notice = MutableStateFlow<UiText?>(null)

    private val _effects = Channel<ShareDocumentEffect>(Channel.BUFFERED)
    val effects: Flow<ShareDocumentEffect> = _effects.receiveAsFlow()

    val state: StateFlow<ShareDocumentUiState?> = combine(
        observeDetail(documentId)
            .map { detail -> detail?.let(::toUiState) }
            .catch { cause ->
                telemetry.readFailed(DocumentVaultTelemetry.Screen.SHARE, cause)
                emit(null)
            },
        notice,
    ) { sheet, notice -> sheet?.copy(notice = notice) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    init {
        telemetry.shareOpened()
    }

    fun onEvent(event: ShareDocumentEvent) {
        val document = document ?: return
        when (event) {
            ShareDocumentEvent.ShareTapped -> share(document)
            ShareDocumentEvent.DownloadTapped -> saveCopy(document)
        }
    }

    /** Export a copy the receiving app is allowed to read, then hand it over. */
    private fun share(document: Document) {
        notice.update { null }
        viewModelScope.launch {
            exportFile(document).fold(
                ifLeft = { notice.update { UiText(Res.string.dv_share_failed) } },
                ifRight = { key ->
                    state.value?.let { telemetry.documentShared(it.type, ShareTarget.SYSTEM.name) }
                    _effects.trySend(
                        ShareDocumentEffect.ShareFile(key, StoredFileKinds.mimeTypeOf(key)),
                    )
                },
            )
        }
    }

    /**
     * Put a copy where the owner keeps their downloads.
     *
     * Reported on the sheet rather than as a handoff: nothing opens, so without a line
     * saying it landed the owner has no way to tell the tap from a dead button.
     */
    private fun saveCopy(document: Document) {
        notice.update { null }
        viewModelScope.launch {
            downloads.saveCopy(
                storageKey = document.storagePath,
                fileName = document.exportFileName(),
                mimeType = StoredFileKinds.mimeTypeOf(document.storagePath),
            ).fold(
                ifLeft = { notice.update { UiText(Res.string.dv_share_save_failed) } },
                ifRight = {
                    state.value?.let { telemetry.documentShared(it.type, ShareTarget.DOWNLOADS.name) }
                    notice.update { UiText(Res.string.dv_share_saved) }
                },
            )
        }
    }

    private fun toUiState(detail: DocumentDetail): ShareDocumentUiState {
        document = detail.document
        return ShareDocumentUiState(
            id = detail.document.id,
            type = detail.document.type,
            title = detail.document.title?.value,
            validity = detail.validity,
            isFileAvailable = detail.isFileAvailable,
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
