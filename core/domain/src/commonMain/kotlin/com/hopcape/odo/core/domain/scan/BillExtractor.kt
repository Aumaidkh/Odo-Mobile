package com.hopcape.odo.core.domain.scan

import arrow.core.Either
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port that reads a bill photo and answers with what it could make out.
 *
 * The unstable dependency of the whole product sits behind this one interface. The real
 * adapter posts the image to the `ai-bill-scan` Edge Function, which holds the Anthropic key,
 * enforces the quota and runs the confidence gate server-side (TDD §7.1) — none of which the
 * app is allowed to do itself. A fake adapter makes every screen above here testable without
 * spending a token.
 *
 * Failure is an [Either], not an exception: a photo that cannot be read is an ordinary
 * outcome of pointing a camera at a crumpled receipt, and the flow it leads to (retake, or
 * enter it by hand) is a feature rather than an error path.
 */
fun interface BillExtractor {

    /**
     * Read [image].
     *
     * Answers [DomainError.ScanUnreadable] when the photo yielded nothing usable, and
     * [DomainError.ScanQuotaExhausted] when the owner's plan has no scans left — the caller
     * tells those two apart because one offers a retake and the other offers Pro.
     */
    suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedBill>
}
