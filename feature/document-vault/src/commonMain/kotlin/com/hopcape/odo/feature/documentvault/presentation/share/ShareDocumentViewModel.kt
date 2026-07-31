package com.hopcape.odo.feature.documentvault.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.feature.documentvault.domain.usecase.DocumentDetail
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the share sheet. Reads the document being shared, and hands its file to
 * the route when a target is picked.
 *
 * The sheet emits `null` state until the document loads; a sheet that opens on nothing is
 * better than one that offers to share a document it has not read.
 */
internal class ShareDocumentViewModel(
    documentId: DocumentId,
    observeDetail: ObserveDocumentDetailUseCase,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    private var storagePath: String? = null

    private val _effects = Channel<ShareDocumentEffect>(Channel.BUFFERED)
    val effects: Flow<ShareDocumentEffect> = _effects.receiveAsFlow()

    val state: StateFlow<ShareDocumentUiState?> = observeDetail(documentId)
        .map { detail -> detail?.let(::toUiState) }
        .catch { cause ->
            telemetry.readFailed(DocumentVaultTelemetry.Screen.SHARE, cause)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    init {
        telemetry.shareOpened()
    }

    fun onEvent(event: ShareDocumentEvent) {
        val path = storagePath ?: return
        when (event) {
            is ShareDocumentEvent.ShareVia -> {
                state.value?.let { telemetry.documentShared(it.type, event.target.name) }
                emit(ShareDocumentEffect.ShareFile(path, event.target))
            }

            ShareDocumentEvent.DownloadTapped -> emit(ShareDocumentEffect.DownloadFile(path))
        }
    }

    private fun emit(effect: ShareDocumentEffect) {
        _effects.trySend(effect)
    }

    private fun toUiState(detail: DocumentDetail): ShareDocumentUiState {
        storagePath = detail.document.storagePath
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
