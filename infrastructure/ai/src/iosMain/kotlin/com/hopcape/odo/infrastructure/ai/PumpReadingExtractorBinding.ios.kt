package com.hopcape.odo.infrastructure.ai

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.scan.PumpReadingExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedPumpReading
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import org.koin.core.scope.Scope

/**
 * iOS has no on-device reader — ML Kit ships no Kotlin/iOS artifact. Scans answer
 * [DomainError.ScanUnavailable], which the scan flow already turns into the manual-entry
 * offer.
 */
internal actual fun Scope.platformPumpReadingExtractor(): PumpReadingExtractor =
    UnavailablePumpReadingExtractor

private object UnavailablePumpReadingExtractor : PumpReadingExtractor {
    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedPumpReading> =
        DomainError.ScanUnavailable.left()
}
