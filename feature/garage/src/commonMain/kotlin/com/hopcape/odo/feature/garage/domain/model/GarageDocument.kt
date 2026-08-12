package com.hopcape.odo.feature.garage.domain.model

import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import kotlinx.datetime.LocalDate

/**
 * One row of the garage's document overview — the paper, or the gap where it should be.
 *
 * The garage lists a fixed set of papers a car is expected to carry ([TRACKED]) and says
 * something about each one, including the ones that aren't on file yet. Modelling that as
 * two cases rather than a nullable document plus a "missing" flag means an on-file row
 * always has both its [Document] and its resolved [DocumentValidity], and a missing row
 * can never claim an expiry.
 *
 * Garage-specific: the vault owns documents themselves (shared kernel), while "which
 * papers this car ought to have, and how it's doing" is this feature's view of them.
 */
internal sealed interface GarageDocument {

    val type: DocumentType

    /** On file, with its standing already resolved against the day being read on. */
    data class OnFile(
        val document: Document,
        val validity: DocumentValidity,
    ) : GarageDocument {
        override val type: DocumentType get() = document.type

        /** Lapsed or about to lapse — the rows that should read as work to do. */
        val needsAttention: Boolean get() = validity.needsAttention
    }

    /** Expected, but nothing uploaded yet — the row offers to add it. */
    data class Missing(override val type: DocumentType) : GarageDocument

    companion object {
        /**
         * The papers the garage asks after, in the order it shows them. Loan letters and
         * "other" documents live in the vault but aren't chased here: not every car has
         * one, so an empty row would be noise rather than a nudge.
         */
        val TRACKED: List<DocumentType> = listOf(DocumentType.INSURANCE, DocumentType.PUC, DocumentType.RC)

        /**
         * Builds one row per [TRACKED] paper from whatever the car actually has, resolving
         * each on-file document against [today]. Where a car holds several documents of the
         * same type (last year's policy and this year's), the one that runs longest wins —
         * that is the one still covering the owner.
         */
        fun rowsFor(documents: List<Document>, today: LocalDate): List<GarageDocument> =
            TRACKED.map { type ->
                val newest = documents
                    .filter { it.type == type }
                    .maxByOrNull { it.expiresOn ?: LocalDate(YEAR_FAR_FUTURE, 1, 1) }
                if (newest == null) Missing(type) else OnFile(newest, newest.validity(today))
            }

        /** Stands in for "never expires" when ordering by expiry. */
        private const val YEAR_FAR_FUTURE = 9999
    }
}
