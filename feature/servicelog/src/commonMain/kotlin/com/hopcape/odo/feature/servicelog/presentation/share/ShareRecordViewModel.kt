package com.hopcape.odo.feature.servicelog.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StorageKey
import com.hopcape.odo.core.platform.share.EXPORT_DIRECTORY
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceRecordUseCase
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceRecordDocumentFactory
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
 * State holder for the "share verified record" sheet.
 *
 * Decides everything about the document — what goes in it, what it is called, where it is
 * written, and whether it can be sent. The host does the two things that need a UI to exist:
 * running the platform's renderer, and opening the system share sheet.
 *
 * **Rendered once per sheet.** The first target the owner taps produces the file; every
 * target after that sends the same one. A record does not change while a sheet is open over
 * it, and laying out forty entries twice to send the same document twice is time the owner
 * spends looking at a spinner.
 */
internal class ShareRecordViewModel(
    private val carId: CarId,
    private val observeRecord: ObserveServiceRecordUseCase,
    private val documents: ServiceRecordDocumentFactory,
    private val files: PlatformFileStore,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareRecordUiState())
    val state: StateFlow<ShareRecordUiState> = _state.asStateFlow()

    private val _effects = Channel<ShareRecordEffect>(Channel.BUFFERED)
    val effects: Flow<ShareRecordEffect> = _effects.receiveAsFlow()

    /** The record as last read. Null until the first emission, which is why sharing waits. */
    private var record: ServiceRecord? = null

    /** The document, once written. Reused by every later target. */
    private var writtenKey: String? = null

    /** What the file is called, kept beside the key so the share sheet can offer a name. */
    private var documentTitle: String? = null

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
        is ShareRecordEvent.ShareViaClicked -> share(event.target)
        is ShareRecordEvent.Rendered -> onRendered(event.bytes, event.target)
    }

    /**
     * Send the record. Builds the document the first time and reuses it after.
     *
     * Ignored outright while one is already being produced: the sheet disables its buttons
     * during a render, and this is the half of that rule a missed frame cannot get around.
     */
    private fun share(target: ShareTarget) {
        if (_state.value.isBusy) return
        val record = record ?: return

        writtenKey?.let { key ->
            telemetry.recordShared(target.name)
            emit(ShareRecordEffect.ShareFile(key, documentTitle.orEmpty()))
            return
        }

        _state.update { it.copy(export = ExportUiState.Rendering(target)) }
        viewModelScope.launch {
            val document = documents.create(record)
            documentTitle = document.name
            emit(
                ShareRecordEffect.RenderDocument(
                    html = document.html,
                    documentName = document.name,
                    target = target,
                ),
            )
        }
    }

    /** Write what the host rendered, then send it. */
    private fun onRendered(bytes: ByteArray?, target: ShareTarget) {
        if (bytes == null || bytes.isEmpty()) {
            fail(target)
            return
        }

        viewModelScope.launch {
            val key = StorageKey.of(
                directory = "$EXPORT_DIRECTORY/${carId.value}",
                fileName = FILE_NAME,
                rawExtension = PDF_EXTENSION,
            )
            files.write(key, bytes).fold(
                ifLeft = { fail(target) },
                ifRight = { written ->
                    writtenKey = written
                    _state.update { it.copy(export = ExportUiState.Idle) }
                    telemetry.recordShared(target.name)
                    emit(ShareRecordEffect.ShareFile(written, documentTitle.orEmpty()))
                },
            )
        }
    }

    private fun fail(target: ShareTarget) {
        telemetry.readFailed(
            ServiceLogTelemetry.Source.SHARE,
            IllegalStateException("record pdf could not be produced for ${target.name}"),
        )
        _state.update { it.copy(export = ExportUiState.Failed) }
    }

    private fun show(record: ServiceRecord) {
        this.record = record
        // A record that has changed invalidates the file written from the older one, so the
        // next target renders again rather than sending a document that is already stale.
        writtenKey = null
        _state.update {
            it.copy(
                content = ShareRecordUiState.Content.Loaded(
                    carName = record.carName.takeIf { name -> name.isNotBlank() },
                    verifiedCount = record.verifiedCount,
                    serviceCount = record.entryCount,
                ),
            )
        }
    }

    private fun emit(effect: ShareRecordEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        /**
         * One file per car, overwritten on every export. The export directory is what the
         * share sheet reads from, and a name carrying a date would leave a copy of every
         * record the owner ever sent sitting in it.
         */
        const val FILE_NAME = "service-record"
        const val PDF_EXTENSION = "pdf"
    }
}
