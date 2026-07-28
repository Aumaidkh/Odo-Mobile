package com.hopcape.odo.core.domain.document.model

import com.hopcape.odo.core.domain.car.model.CarId
import kotlinx.datetime.LocalDate

/**
 * A paper in a car's vault — insurance, PUC, RC, a loan letter.
 *
 * Shared kernel, because a document is never only the vault's: the garage shows which
 * ones are on file, the timeline logs their renewals, the reminder engine watches their
 * expiry, and the resale passport lists them. Mirrors the `documents` table
 * (DB_SCHEMA §9.7) — every column that is domain-meaningful, none that are storage detail.
 *
 * The only invariant worth enforcing is typing, which the value objects already carry, so
 * there is no validating factory here: a [Document] is exactly its typed fields plus the
 * one rule that matters — [validity].
 */
data class Document(
    val id: DocumentId,
    val carId: CarId,
    val type: DocumentType,
    /** The owner's own label ("SafeDrive comprehensive"); absent falls back to [type]. */
    val title: String?,
    val issuedOn: LocalDate?,
    /** Null for papers that never lapse — see [DocumentValidity.NoExpiry]. */
    val expiresOn: LocalDate?,
) {
    /**
     * This document's standing on [today] — valid, due for renewal, or lapsed. Derived,
     * never stored, so it can never go stale in the way a persisted status would.
     */
    fun validity(today: LocalDate): DocumentValidity = DocumentValidity.of(expiresOn, today)

    /** Shorthand for the question most callers actually ask. */
    fun needsAttentionOn(today: LocalDate): Boolean = validity(today).needsAttention
}
