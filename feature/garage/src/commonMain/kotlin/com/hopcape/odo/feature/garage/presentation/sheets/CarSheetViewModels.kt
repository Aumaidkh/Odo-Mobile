package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StorageKey
import com.hopcape.odo.core.platform.share.EXPORT_DIRECTORY
import com.hopcape.odo.feature.garage.domain.usecase.CarDetails
import com.hopcape.odo.feature.garage.domain.usecase.GarageSnapshot
import com.hopcape.odo.feature.garage.domain.usecase.ObserveCarDetailsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.domain.usecase.RemoveCarUseCase
import com.hopcape.odo.feature.garage.presentation.GarageTelemetry
import com.hopcape.odo.feature.garage.presentation.sheets.pdf.CarDetailsDocumentFactory
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_no_car
import com.hopcape.odo.feature.garage.resources.gr_error_remove_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reads the car once for whichever sheet asked. A snapshot rather than a stream: a sheet is
 * open for a few seconds and is about the car as it stands, and re-reading under the
 * owner's finger is how a confirmation ends up describing something else.
 */
private suspend fun loadSummary(
    activeCar: ActiveCarProvider,
    observeGarage: ObserveGarageUseCase,
): Loadable<CarSummary> {
    val carId = activeCar.activeCarId.value ?: return Loadable.Failed(UiText(Res.string.gr_error_no_car))
    val snapshot = observeGarage(carId).first()
    return snapshot.toSummary() ?: Loadable.Failed(UiText(Res.string.gr_error_no_car))
}

private fun GarageSnapshot.toSummary(): Loadable.Ready<CarSummary>? {
    val car = car ?: return null
    return Loadable.Ready(
        CarSummary(
            displayName = car.displayName,
            registration = car.registrationNumber?.value,
            serviceCount = history.size,
            documentCount = documents.count { it is com.hopcape.odo.feature.garage.domain.model.GarageDocument.OnFile },
        ),
    )
}

/** State holder for the car-actions sheet — a header and three ways onward. */
internal class CarActionsViewModel(
    private val activeCar: ActiveCarProvider,
    private val observeGarage: ObserveGarageUseCase,
    private val telemetry: GarageTelemetry,
    config: FeatureConfig,
) : ViewModel() {

    // Read once, when the sheet is built. A flip lands on the next time it is opened, which
    // is what a launch gate needs — the sheet is a router and has nothing to re-read.
    private val _state = MutableStateFlow(CarActionsUiState(showChecklist = config.serviceChecklistEnabled))
    val state: StateFlow<CarActionsUiState> = _state.asStateFlow()

    private val _effects = Channel<CarActionsEffect>(Channel.BUFFERED)
    val effects: Flow<CarActionsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val car = loadSummary(activeCar, observeGarage)
            if (car is Loadable.Failed) telemetry.noActiveCar(GarageTelemetry.Screen.CAR_ACTIONS)
            _state.update { it.copy(car = car) }
        }
    }

    fun onEvent(event: CarActionsEvent) {
        val effect = when (event) {
            CarActionsEvent.EditTapped -> CarActionsEffect.OpenEdit
            CarActionsEvent.ValueTapped -> CarActionsEffect.OpenCarValue
            CarActionsEvent.ChecklistTapped -> CarActionsEffect.OpenServiceChecklist
            CarActionsEvent.ExportTapped -> CarActionsEffect.OpenExport
            CarActionsEvent.RemoveTapped -> CarActionsEffect.OpenRemove
        }
        _effects.trySend(effect)
    }
}

/**
 * State holder for the remove-car confirmation.
 *
 * The counts on the sheet are what the owner is giving up, read before the tap rather than
 * after it — a confirmation that cannot say what it deletes is not a confirmation.
 */
internal class RemoveCarViewModel(
    private val activeCar: ActiveCarProvider,
    private val observeGarage: ObserveGarageUseCase,
    private val removeCar: RemoveCarUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(RemoveCarUiState())
    val state: StateFlow<RemoveCarUiState> = _state.asStateFlow()

    private val _effects = Channel<RemoveCarEffect>(Channel.BUFFERED)
    val effects: Flow<RemoveCarEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val car = loadSummary(activeCar, observeGarage)
            if (car is Loadable.Failed) telemetry.noActiveCar(GarageTelemetry.Screen.REMOVE_CAR)
            _state.update { it.copy(car = car) }
        }
    }

    fun onEvent(event: RemoveCarEvent) = when (event) {
        RemoveCarEvent.ExportFirstTapped -> emit(RemoveCarEffect.OpenExport)
        RemoveCarEvent.CancelTapped -> emit(RemoveCarEffect.Dismiss)
        RemoveCarEvent.RemoveTapped -> remove()
    }

    private fun remove() {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar(GarageTelemetry.Screen.REMOVE_CAR)
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.gr_error_no_car))) }
            return
        }
        val summary = _state.value.car.valueOrNull

        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(GarageTelemetry.Trace.REMOVE_CAR)) {
            telemetry.carRemove(
                services = summary?.serviceCount ?: 0,
                documents = summary?.documentCount ?: 0,
            ) { removeCar(carId) }.fold(
                ifLeft = {
                    _state.update {
                        it.copy(submission = Submission.Failed(UiText(Res.string.gr_error_remove_failed)))
                    }
                },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    emit(RemoveCarEffect.Removed)
                },
            )
        }
    }

    private fun emit(effect: RemoveCarEffect) {
        _effects.trySend(effect)
        Unit
    }
}

/**
 * State holder for the export sheet.
 *
 * Decides everything about the vehicle-details document — what goes in it, what it is
 * called, where it is written, and whether it can be sent. The host does the two things
 * that need a UI to exist: running the platform's renderer, and opening the system share
 * sheet. The same shape as the service log's share sheet, because it is the same job.
 *
 * **Rendered once per sheet.** The first button the owner taps produces the file; every
 * tap after that sends the same one, until the car's data changes underneath the sheet and
 * invalidates it.
 */
internal class ExportViewModel(
    private val activeCar: ActiveCarProvider,
    private val observeDetails: ObserveCarDetailsUseCase,
    private val documents: CarDetailsDocumentFactory,
    private val files: PlatformFileStore,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val _effects = Channel<ExportEffect>(Channel.BUFFERED)
    val effects: Flow<ExportEffect> = _effects.receiveAsFlow()

    /** The details as last read. Null until the first emission, which is why sharing waits. */
    private var details: CarDetails? = null

    /** The document, once written. Reused by every later tap. */
    private var writtenKey: String? = null

    /** What the file is called, kept beside the key so the share sheet can offer a name. */
    private var documentTitle: String? = null

    init {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar(GarageTelemetry.Screen.EXPORT)
            _state.update { it.copy(car = Loadable.Failed(UiText(Res.string.gr_error_no_car))) }
        } else {
            viewModelScope.launch {
                observeDetails(carId)
                    // Reported rather than thrown: an unhandled failure in a `collect`
                    // cancels the ViewModel's scope, and a sheet with no counts is better
                    // than the sheet taking the screen behind it down.
                    .catch { cause -> telemetry.readFailed(GarageTelemetry.Screen.EXPORT, cause) }
                    .collect(::show)
            }
        }
    }

    fun onEvent(event: ExportEvent) = when (event) {
        ExportEvent.PdfTapped -> share(ExportVia.PDF)
        ExportEvent.ShareTapped -> share(ExportVia.SHARE)
        is ExportEvent.Rendered -> onRendered(event.bytes, event.via)
    }

    /**
     * Send the document. Builds it the first time and reuses it after.
     *
     * Ignored outright while one is already being produced: the sheet disables its buttons
     * during a render, and this is the half of that rule a missed frame cannot get around.
     */
    private fun share(via: ExportVia) {
        if (_state.value.isBusy) return
        val details = details ?: return
        telemetry.exportRequested(via.asTelemetryTarget())

        writtenKey?.let { key ->
            emit(ExportEffect.ShareFile(key, documentTitle.orEmpty()))
            return
        }

        _state.update { it.copy(export = ExportProgress.Rendering(via)) }
        viewModelScope.launch(telemetry.op(GarageTelemetry.Trace.EXPORT_PDF)) {
            val document = documents.create(details)
            documentTitle = document.name
            emit(
                ExportEffect.RenderDocument(
                    html = document.html,
                    documentName = document.name,
                    via = via,
                ),
            )
        }
    }

    /** Write what the host rendered, then send it. */
    private fun onRendered(bytes: ByteArray?, via: ExportVia) {
        val carId = activeCar.activeCarId.value
        if (bytes == null || bytes.isEmpty() || carId == null) {
            fail(via)
            return
        }

        viewModelScope.launch {
            val key = StorageKey.of(
                directory = "$EXPORT_DIRECTORY/${carId.value}",
                fileName = FILE_NAME,
                rawExtension = PDF_EXTENSION,
            )
            files.write(key, bytes).fold(
                ifLeft = { fail(via) },
                ifRight = { written ->
                    writtenKey = written
                    _state.update { it.copy(export = ExportProgress.Idle) }
                    emit(ExportEffect.ShareFile(written, documentTitle.orEmpty()))
                },
            )
        }
    }

    private fun fail(via: ExportVia) {
        telemetry.readFailed(
            GarageTelemetry.Screen.EXPORT,
            IllegalStateException("car details pdf could not be produced for ${via.name}"),
        )
        _state.update { it.copy(export = ExportProgress.Failed) }
    }

    private fun show(details: CarDetails) {
        this.details = details
        // Data that has changed invalidates the file written from the older read, so the
        // next tap renders again rather than sending a document that is already stale.
        writtenKey = null
        val record = details.record
        _state.update {
            it.copy(
                car = if (record.carName.isBlank()) {
                    // The car row has not arrived (or is gone) — the sheet keeps loading
                    // rather than offering a document about nothing.
                    Loadable.Loading
                } else {
                    Loadable.Ready(
                        CarSummary(
                            displayName = record.carName,
                            registration = record.registrationNumber,
                            serviceCount = record.rows.count { row -> row.event is ActivityEvent.Service },
                            documentCount = details.documents.size,
                        ),
                    )
                },
            )
        }
    }

    private fun ExportVia.asTelemetryTarget(): String = when (this) {
        ExportVia.PDF -> GarageTelemetry.ExportTarget.PDF
        ExportVia.SHARE -> GarageTelemetry.ExportTarget.SHARE
    }

    private fun emit(effect: ExportEffect) {
        _effects.trySend(effect)
        Unit
    }

    private companion object {
        /**
         * One file per car, overwritten on every export — a name carrying a date would
         * leave a copy of every document the owner ever sent sitting in the export
         * directory the share sheet reads from.
         */
        const val FILE_NAME = "car-details"
        const val PDF_EXTENSION = "pdf"
    }
}
