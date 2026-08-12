package com.hopcape.odo.feature.servicelog.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveShareableRecordUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ShareableRecord
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the "share verified record" sheet. Holds [ShareRecordUiState], consumes
 * [ShareRecordEvent]s, and emits one-shot [ShareRecordEffect]s.
 *
 * The sheet is a destination like any other, so it gets a state holder like any other: the
 * counts it claims come from the same observed read as the list behind it, and the "Copied"
 * confirmation lives here rather than in a `remember` that a recomposition owns.
 *
 * Sharing itself is the host's job — this decides *what* would be shared and whether there
 * is anything to share it with.
 */
internal class ShareRecordViewModel(
    carId: CarId,
    observeRecord: ObserveShareableRecordUseCase,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareRecordUiState())
    val state: StateFlow<ShareRecordUiState> = _state.asStateFlow()

    private val _effects = Channel<ShareRecordEffect>(Channel.BUFFERED)
    val effects: Flow<ShareRecordEffect> = _effects.receiveAsFlow()

    init {
        telemetry.shareOpened()
        viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.SHARE_LOAD)) {
            observeRecord(carId)
                // Reported rather than thrown: an unhandled failure in a `collect` cancels
                // the ViewModel's scope, and the sheet showing no counts is better than the
                // sheet taking the screen behind it down.
                .catch { cause -> telemetry.readFailed(ServiceLogTelemetry.Source.SHARE, cause) }
                .collect(::show)
        }
    }

    fun onEvent(event: ShareRecordEvent) = when (event) {
        ShareRecordEvent.CopyLinkClicked -> copyLink()
        is ShareRecordEvent.ShareViaClicked -> shareVia(event.target)
        ShareRecordEvent.DownloadPdfClicked -> downloadPdf()
    }

    /**
     * Copy the link and say so. Both halves are conditional on there being a link at all:
     * until the Resale Passport issues one there is nothing to put on the clipboard, and
     * confirming a copy that didn't happen is the one thing worse than no confirmation.
     */
    private fun copyLink() = withLink { url ->
        telemetry.recordShared(COPY_TARGET)
        _state.update { it.copy(link = PassportLinkUiState.Ready(url, copied = true)) }
        emit(ShareRecordEffect.CopyLink(url))
    }

    private fun shareVia(target: ShareTarget) = withLink { url ->
        telemetry.recordShared(target.name)
        emit(ShareRecordEffect.ShareLink(target, url))
    }

    private fun downloadPdf() {
        telemetry.recordShared(PDF_TARGET)
        emit(ShareRecordEffect.DownloadPdf)
    }

    /** Run [action] only when there is a link to act on. */
    private inline fun withLink(action: (String) -> Unit) {
        val link = _state.value.link
        if (link is PassportLinkUiState.Ready) action(link.url)
    }

    private fun show(record: ShareableRecord) = _state.update {
        it.copy(
            content = ShareRecordUiState.Content.Loaded(
                carName = record.carName,
                verifiedCount = record.verifiedCount,
                serviceCount = record.serviceCount,
            ),
            // Re-reading the record must not clear a "Copied" the owner just saw, so the
            // link state is only rebuilt when the URL itself changes.
            link = it.link.forUrl(record.passportUrl),
        )
    }

    private fun emit(effect: ShareRecordEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val COPY_TARGET = "copy_link"
        const val PDF_TARGET = "pdf"
    }
}

/** The link state for [url], keeping a just-copied confirmation when the URL is unchanged. */
private fun PassportLinkUiState.forUrl(url: String?): PassportLinkUiState = when {
    url == null -> PassportLinkUiState.Unavailable
    this is PassportLinkUiState.Ready && this.url == url -> this
    else -> PassportLinkUiState.Ready(url)
}
