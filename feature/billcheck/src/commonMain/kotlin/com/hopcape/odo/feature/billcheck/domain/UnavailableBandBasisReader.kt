package com.hopcape.odo.feature.billcheck.domain

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Answers nothing, on purpose.
 *
 * "How we know" is the screen that says a band is defensible, and the reader that can produce
 * one is the next slice — it needs the band the check already resolved rather than a second
 * lookup. Until then this refuses, because the alternative was a fixture's band shown beside a
 * real finding: a Delhi owner flagged against a real Delhi figure, tapping through to a
 * Srinagar one. That is the unsourced number this feature exists to argue against.
 */
internal class UnavailableBandBasisReader : BandBasisReader {

    override suspend fun basisFor(
        billId: String,
        lineName: String,
    ): Either<DomainError, BandBasis> = DomainError.LookupUnavailable.left()
}
