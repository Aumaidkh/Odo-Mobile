package com.hopcape.odo.feature.servicelog.presentation.share

import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/**
 * Where a verified record can be sent.
 *
 * Every one of these produces the same PDF and opens the same system sheet — they differ
 * only in what gets reported to analytics, because which app an owner reaches for is worth
 * knowing and the system sheet never tells us. [DOWNLOAD] is a target like the rest: saving
 * to Files or Drive is one of the choices inside that sheet.
 */
internal enum class ShareTarget { WHATSAPP, EMAIL, MORE, DOWNLOAD }

/**
 * How far along producing the document is.
 *
 * A record of forty entries takes a moment to lay out and print, so the sheet has to say
 * something during it — and has to stop taking taps, because a second tap would render the
 * same document again.
 */
internal sealed interface ExportUiState {

    /** Nothing in progress. Every target is tappable. */
    data object Idle : ExportUiState

    /** Rendering, for [target]. The sheet marks that button and disables all of them. */
    data class Rendering(val target: ShareTarget) : ExportUiState

    /**
     * The document could not be produced. Kept in state rather than shown once and lost, so
     * the owner sees why nothing happened instead of a button that did nothing.
     */
    data object Failed : ExportUiState
}

/** The record being shared: what the sheet says about it, and how the export is going. */
internal data class ShareRecordUiState(
    val content: Content = Content.Loading,
    val export: ExportUiState = ExportUiState.Idle,
    /**
     * Shown when the free exports are spent (#246): Growth Plan v3 sells the record two
     * ways, and someone selling their car wants the PDF once and will never want a
     * subscription. Null until they actually run out — nothing is offered before it is
     * needed. [oneTimePrice] is the store's own formatted string, null when the store has
     * no such product, in which case only the Pro route is offered.
     */
    val exportOffer: ExportOffer? = null,
) {
    /** True while a document is being produced — every target is disabled. */
    val isBusy: Boolean get() = export is ExportUiState.Rendering

    /** The two ways to pay for a record export, once the free ones are gone (#246). */
    data class ExportOffer(
        /** The store's price for a single export, e.g. "₹99". Null when it cannot be sold. */
        val oneTimePrice: String?,
        /** True while the store's purchase sheet is open. */
        val buying: Boolean = false,
    )

    sealed interface Content {
        data object Loading : Content

        /**
         * [carName] is null when the car couldn't be named — the sheet still shows the
         * counts, which are the part a buyer cares about.
         */
        data class Loaded(
            val carName: String?,
            val verifiedCount: Int,
            val serviceCount: Int,
        ) : Content

        /**
         * The sheet was opened on one entry, so the document going out is that entry's
         * bill — and the line under the title says which bill, not how the whole record
         * is doing.
         */
        data class LoadedBill(
            val carName: String?,
            val serviceDate: LocalDate,
            val amount: Amount,
        ) : Content
    }
}
