package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.scan.DocumentExtractor
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanCharger
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock

/**
 * Reads a photographed paper — insurance, PUC, RC or a licence — for its type and dates.
 *
 * A separate use case from [ScanBillUseCase] rather than one with a mode flag, because the
 * two disagree about what "usable" means: a bill with no odometer is still worth reviewing,
 * while a document with no expiry cannot do the single job the vault needs it for.
 */
internal class ScanDocumentUseCase(
    private val extractor: DocumentExtractor,
    private val allowance: ScanAllowance,
    private val charger: ScanCharger,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(storageKey: String): Either<DomainError, ExtractedDocument> = either {
        val limit = allowance.current()
        ensure(limit.allowsAnother) { DomainError.ScanQuotaExhausted(limit.cap ?: 0) }

        val image = ScannedImage(
            id = ScanId.new(ids),
            storageKey = storageKey,
            capturedAt = clock.now(),
        )
        val document = extractor.extract(image).bind()
        ensure(!document.isEmpty) { DomainError.ScanRejected }
        // Counted only now, for the same reason as ScanBillUseCase: a read that gave the
        // owner nothing does not spend one of their three.
        charger.chargeOne()
        document
    }
}
