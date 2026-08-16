package com.hopcape.odo.feature.servicelog.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StorageKey
import com.hopcape.odo.core.platform.share.EXPORT_DIRECTORY
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveEntryDetailUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceRecordUseCase
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceBillDocumentFactory
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceRecordDocument
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceRecordDocumentFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hopcape.odo.core.domain.record.entitlement.ExportCredits
import com.hopcape.odo.core.domain.record.entitlement.RecordExportUsage
import com.hopcape.odo.core.domain.subscription.OneTimeProducts
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser

/**
 * State holder for the "share verified record" sheet.
 *
 * Decides everything about the document — what goes in it, what it is called, where it is
 * written, and whether it can be sent. The host does the two things that need a UI to exist:
 * running the platform's renderer, and opening the system share sheet.
 *
 * Two documents can come out of it, decided by [logId]. Opened without one (the list, the
 * Timeline) the document is the car's whole verified record; opened with one (an entry's
 * detail) it is that entry's bill — its items, its workshop, its total — because the owner
 * standing on one bill is sharing that bill, not the car's life story.
 *
 * **Rendered once per sheet.** The first target the owner taps produces the file; every
 * target after that sends the same one. A record does not change while a sheet is open over
 * it, and laying out forty entries twice to send the same document twice is time the owner
 * spends looking at a spinner.
 */
internal class ShareRecordViewModel(
    private val carId: CarId,
    private val logId: ServiceLogId?,
    private val observeRecord: ObserveServiceRecordUseCase,
    private val observeDetail: ObserveEntryDetailUseCase,
    private val entitlements: EntitlementSource,
    private val exportUsage: RecordExportUsage,
    private val exportCredits: ExportCredits,
    private val oneTimePurchaser: OneTimePurchaser,
    private val documents: ServiceRecordDocumentFactory,
    private val bills: ServiceBillDocumentFactory,
    private val files: PlatformFileStore,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareRecordUiState())
    val state: StateFlow<ShareRecordUiState> = _state.asStateFlow()

    private val _effects = Channel<ShareRecordEffect>(Channel.BUFFERED)
    val effects: Flow<ShareRecordEffect> = _effects.receiveAsFlow()

    /** The record as last read. Null until the first emission, which is why sharing waits. */
    private var record: ServiceRecord? = null

    /** The entry being shared as a bill. Stays null for a whole-record sheet. */
    private var entry: ServiceLogEntry? = null

    /** The document, once written. Reused by every later target. */
    private var writtenKey: String? = null

    /** What the file is called, kept beside the key so the share sheet can offer a name. */
    private var documentTitle: String? = null

    init {
        telemetry.shareOpened()
        viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.SHARE_LOAD)) {
            observed()
                // Reported rather than thrown: an unhandled failure in a `collect` cancels
                // the ViewModel's scope, and the sheet showing no counts is better than the
                // sheet taking the screen behind it down.
                .catch { cause -> telemetry.readFailed(ServiceLogTelemetry.Source.SHARE, cause) }
                .collect { (record, entry) -> show(record, entry) }
        }
    }

    /**
     * What the sheet watches: the record alone, or — for a bill — the record and the entry
     * together. The record rides along either way because even the bill document opens with
     * the car's identity, which no single entry carries.
     */
    private fun observed(): Flow<Pair<ServiceRecord, ServiceLogEntry?>> {
        val records = observeRecord(carId)
        val logId = logId ?: return records.map { record -> record to null }
        return combine(records, observeDetail(carId, logId)) { record, detail ->
            record to detail?.entry
        }
    }

    fun onEvent(event: ShareRecordEvent) = when (event) {
        is ShareRecordEvent.ShareViaClicked -> share(event.target)
        is ShareRecordEvent.Rendered -> onRendered(event.bytes, event.target)
        ShareRecordEvent.BuyExportClicked -> buyExport()
        ShareRecordEvent.UnlockWithProClicked -> {
            _state.update { it.copy(exportOffer = null) }
            emit(ShareRecordEffect.OpenPaywall)
        }
        ShareRecordEvent.ExportOfferDismissed -> {
            pendingTarget = null
            _state.update { it.copy(exportOffer = null) }
        }
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
        // A bill sheet with no entry yet (or no entry any more) has nothing to print.
        if (logId != null && entry == null) return

        // The whole record is what Pro sells. One entry's bill is not: the owner is sharing
        // the bill they just paid, and charging for that would be charging for their own
        // receipt.
        if (logId == null) {
            viewModelScope.launch {
                // Counted, not on/off: the free plan grants a few whole-record exports and
                // then stops. `has()` would be the wrong question — it answers true for any
                // quota above none, so it would hand the feature over on the free plan.
                //
                // An already-rendered document is free to send again. The owner has spent the
                // export; charging a second time because they also wanted to email what they
                // just sent on WhatsApp would be charging for the share sheet, not the export.
                val quota = entitlements.observe().first().quotaFor(ProFeature.RECORD_EXPORT)
                when {
                    writtenKey != null || quota.allowsAnother(exportUsage.used()) -> startShare(target, record)

                    // A credit bought earlier is spent here, at the same moment a free
                    // export would have been — one PDF, which is what the purchase copy
                    // says. Spending is guarded in storage, so two shares racing cannot
                    // both take the last one.
                    exportCredits.spend() -> startShare(target, record)

                    // Out of both. Rather than sending them straight to a subscription,
                    // offer the one-off too (#246): someone selling their car wants this
                    // PDF once and will never want a plan.
                    else -> {
                        telemetry.recordExportLocked()
                        pendingTarget = target
                        _state.update {
                            it.copy(
                                exportOffer = ShareRecordUiState.ExportOffer(
                                    oneTimePrice = oneTimePurchaser.priceOf(OneTimeProducts.RECORD_EXPORT),
                                ),
                            )
                        }
                    }
                }
            }
            return
        }
        startShare(target, record)
    }

    /** Which target the owner asked for before they were told they were out of exports. */
    private var pendingTarget: ShareTarget? = null

    /**
     * Buy one export, then take the share they originally asked for.
     *
     * The credit is granted and immediately spent rather than left on the balance: the
     * owner tapped a share target, was told the price, and paid — stopping there to make
     * them tap the target again would be the app taking their money and then asking what
     * they wanted.
     */
    private fun buyExport() {
        val record = record ?: return
        val target = pendingTarget ?: return
        _state.update { it.copy(exportOffer = it.exportOffer?.copy(buying = true)) }
        viewModelScope.launch {
            oneTimePurchaser.purchase(OneTimeProducts.RECORD_EXPORT).fold(
                ifLeft = {
                    // Cancelling is the common ending and is not an error; either way the
                    // offer goes away rather than sitting there mid-purchase.
                    _state.update { state -> state.copy(exportOffer = state.exportOffer?.copy(buying = false)) }
                },
                ifRight = {
                    exportCredits.grant()
                    exportCredits.spend()
                    _state.update { state -> state.copy(exportOffer = null) }
                    startShare(target, record)
                },
            )
        }
    }

    /** The share itself, once it is allowed. */
    private fun startShare(target: ShareTarget, record: ServiceRecord) {
        writtenKey?.let { key ->
            telemetry.recordShared(target.name)
            emit(ShareRecordEffect.ShareFile(key, documentTitle.orEmpty()))
            return
        }

        _state.update { it.copy(export = ExportUiState.Rendering(target)) }
        // Traced: laying out a long history is the slowest thing the sheet does, and it is
        // the number to look at when an owner says sharing takes too long.
        viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.RECORD_EXPORT)) {
            val document = buildDocument(record)
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

    /** The whole record, or — when the sheet was opened on one entry — that entry's bill. */
    private suspend fun buildDocument(record: ServiceRecord): ServiceRecordDocument {
        val entry = entry ?: return documents.create(record)
        return bills.create(record, entry)
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
                fileName = if (logId == null) RECORD_FILE_NAME else BILL_FILE_NAME,
                rawExtension = PDF_EXTENSION,
            )
            files.write(key, bytes).fold(
                ifLeft = { fail(target) },
                ifRight = { written ->
                    writtenKey = written
                    // Charged here rather than on the tap: this is the first point at which
                    // the PDF exists. A render that failed gave the owner nothing, and taking
                    // one of three for it would make a broken export cost the same as a good
                    // one. Only the whole-record export is counted — a single bill is free,
                    // and it never reaches this branch with a null logId.
                    if (logId == null) exportUsage.recordExport()
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

    private fun show(record: ServiceRecord, entry: ServiceLogEntry?) {
        this.record = record
        this.entry = entry
        // A record that has changed invalidates the file written from the older one, so the
        // next target renders again rather than sending a document that is already stale.
        writtenKey = null
        val carName = record.carName.takeIf { name -> name.isNotBlank() }
        _state.update {
            it.copy(
                content = if (logId == null) {
                    ShareRecordUiState.Content.Loaded(
                        carName = carName,
                        verifiedCount = record.verifiedCount,
                        serviceCount = record.entryCount,
                    )
                } else if (entry == null) {
                    // The entry is gone — never written, or deleted under the open sheet.
                    // The sheet keeps loading rather than describing a bill it cannot print.
                    ShareRecordUiState.Content.Loading
                } else {
                    ShareRecordUiState.Content.LoadedBill(
                        carName = carName,
                        serviceDate = entry.serviceDate,
                        amount = entry.totalAmount,
                    )
                },
            )
        }
    }

    private fun emit(effect: ShareRecordEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        /**
         * One file of each kind per car, overwritten on every export. The export directory
         * is what the share sheet reads from, and a name carrying a date or an id would
         * leave a copy of every document the owner ever sent sitting in it.
         */
        const val RECORD_FILE_NAME = "service-record"
        const val BILL_FILE_NAME = "service-bill"
        const val PDF_EXTENSION = "pdf"
    }
}
