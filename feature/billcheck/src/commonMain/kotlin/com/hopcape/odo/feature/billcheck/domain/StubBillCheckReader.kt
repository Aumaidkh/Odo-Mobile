package com.hopcape.odo.feature.billcheck.domain

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The two scenes, answered from the preview fixtures.
 *
 * A stub rather than a half-built reader. The real one is the reference tables plus an Edge
 * Function (AI_ADVISORY_PLAN §5), and the tables are hand-typed data that has not been
 * entered yet — anything wired to them today would answer nothing on a real device, which is
 * the one thing that makes a UI impossible to check.
 *
 * It never fails. A stub that invented failures would have the screens tested against a
 * fiction; the real reader's failures are its own to model.
 */
internal class StubBillCheckReader : BillCheckReader, BandBasisReader {

    override suspend fun read(billId: String): Either<DomainError, BillCheck> =
        BillCheckFixtures.monthSix.right()

    override suspend fun basisFor(
        billId: String,
        lineName: String,
    ): Either<DomainError, BandBasis> = BillCheckFixtures.acServiceBasis.right()
}
