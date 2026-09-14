package com.hopcape.odo.feature.billcheck.domain

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Reads a bill and says which lines are worth asking about.
 *
 * A port with a stub behind it today. The real one is the reference tables plus an Edge
 * Function (AI_ADVISORY_PLAN §5), and neither exists yet — the tables are hand-typed data
 * that has not been entered. Wiring the screens to a port now means the day that lands is a
 * binding change and nothing else.
 */
internal fun interface BillCheckReader {

    /** Read the bill behind [billId]. */
    suspend fun read(billId: String): Either<DomainError, BillCheckResult>
}

/**
 * The check, and whether the owner may see it.
 *
 * Both come from the reader because they are one decision. They used to be two — the reader
 * charged and the screen decided what to mask — and the two disagreed in both directions: a
 * masked result was charged for, and a result unmasked after a purchase never was.
 */
internal data class BillCheckResult(val check: BillCheck, val locked: Boolean)

/**
 * Where one line's band came from.
 *
 * Separate from [BillCheckReader] because the sheet is opened per line, long after the check
 * ran, and asking for the whole bill again to explain one row would be the wrong shape.
 */
internal fun interface BandBasisReader {

    suspend fun basisFor(
        billId: String,
        lineName: String,
        /** The job the check resolved, so this cannot answer about a different one. */
        categorySlug: String,
    ): Either<DomainError, BandBasis>
}
