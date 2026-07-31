package com.hopcape.odo.feature.documentvault.presentation.success

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.feature.documentvault.domain.usecase.DocumentDetail
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentDetailUseCase
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the screen shown after a document is saved.
 *
 * It reads the document it just wrote rather than being handed a summary, so the reminder
 * date it promises is the one the reminder policy actually produced for the stored
 * document. A screen that repeats what the form hoped for is a screen that can be wrong.
 */
internal class AddSuccessViewModel(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObserveDocumentDetailUseCase,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    private val documentId = DocumentId(
        requireNotNull(savedStateHandle.get<String>(KEY_DOCUMENT_ID)) {
            "AddSuccess was opened without a document id"
        },
    )

    val state: StateFlow<AddSuccessUiState?> = observeDetail(documentId)
        .map { detail -> detail?.let(::toUiState) }
        .catch { cause ->
            telemetry.readFailed(DocumentVaultTelemetry.Screen.SUCCESS, cause)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    private fun toUiState(detail: DocumentDetail) = AddSuccessUiState(
        type = detail.document.type,
        title = detail.document.title?.value,
        reminder = detail.nextReminder?.let { ReminderPromise(daysBefore = it.daysBefore, on = it.on) },
    )

    companion object {
        /** The key the route writes the document id under. */
        const val KEY_DOCUMENT_ID = "documentId"

        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
