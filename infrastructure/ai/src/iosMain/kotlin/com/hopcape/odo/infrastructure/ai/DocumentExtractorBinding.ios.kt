package com.hopcape.odo.infrastructure.ai

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.scan.DocumentExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import org.koin.core.scope.Scope

/**
 * iOS has no on-device reader — ML Kit ships no Kotlin/iOS artifact and the MVP is
 * Android-only. Scans answer [DomainError.ScanUnavailable]; the confirm screen keeps its
 * date fields typeable, so a paper can still be filed by hand.
 */
internal actual fun Scope.platformDocumentExtractor(): DocumentExtractor = UnavailableDocumentExtractor

private object UnavailableDocumentExtractor : DocumentExtractor {
    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedDocument> =
        DomainError.ScanUnavailable.left()
}
