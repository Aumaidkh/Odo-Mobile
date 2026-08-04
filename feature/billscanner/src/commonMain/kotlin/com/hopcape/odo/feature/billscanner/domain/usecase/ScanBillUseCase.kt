package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock

/**
 * Reads a captured bill photo, having first checked the owner has a scan to spend.
 *
 * The allowance is checked **before** the extractor is called, so an owner out of free scans
 * is told so instead of waiting through an upload that was always going to be refused. The
 * client's count only mirrors the server's, which is what actually enforces the limit
 * (TDD §7.5) — this check is there to fail fast and politely, not to be the gate.
 *
 * An empty result becomes [DomainError.ScanRejected] rather than being handed on: a review
 * screen with every field blank asks the owner to confirm nothing, and the honest next step
 * is a retake or the manual form.
 */
internal class ScanBillUseCase(
    private val extractor: BillExtractor,
    private val allowance: ScanAllowance,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(storageKey: String): Either<DomainError, ExtractedBill> = either {
        val limit = allowance.current()
        ensure(limit.allowsAnother) { DomainError.ScanQuotaExhausted(limit.cap ?: 0) }

        val image = ScannedImage(
            id = ScanId.new(ids),
            storageKey = storageKey,
            capturedAt = clock.now(),
        )
        val bill = extractor.extract(image).bind()
        ensure(!bill.isEmpty) { DomainError.ScanRejected }
        bill
    }
}
