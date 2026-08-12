package com.hopcape.odo.infrastructure.ai

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import org.koin.core.scope.Scope

/**
 * iOS has no on-device reader — ML Kit ships no Kotlin/iOS artifact and the MVP is
 * Android-only. Scans answer [DomainError.ScanUnavailable], which the scan flow already
 * turns into the manual-entry offer.
 */
internal actual fun Scope.platformBillExtractor(): BillExtractor = UnavailableBillExtractor

private object UnavailableBillExtractor : BillExtractor {
    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedBill> =
        DomainError.ScanUnavailable.left()
}
