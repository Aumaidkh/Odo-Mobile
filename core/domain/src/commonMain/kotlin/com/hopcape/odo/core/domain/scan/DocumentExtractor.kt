package com.hopcape.odo.core.domain.scan

import arrow.core.Either
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port that reads a photographed paper and answers with its type and dates.
 *
 * Separate from [BillExtractor] rather than one port with a mode, because the two ask the
 * model for different things and are graded differently: a bill is judged on amounts, a
 * paper on a single expiry date. Splitting them also means the document flow can ship
 * against a different model, or a cheaper one, without touching the bill scanner.
 */
fun interface DocumentExtractor {

    /** Read [image] as a vehicle document. */
    suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedDocument>
}
