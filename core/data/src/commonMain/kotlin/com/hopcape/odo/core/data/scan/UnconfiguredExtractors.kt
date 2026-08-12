package com.hopcape.odo.core.data.scan

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.DocumentExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The extractors in force until the `ai-bill-scan` Edge Function caller lands: they refuse,
 * and say why.
 *
 * Refusing is the only honest option. A stub that returned a plausible-looking bill would put
 * invented amounts in front of an owner and, worse, into their service history — this is the
 * one feature the PRD says must never be wrong. Returning [DomainError.ScanUnavailable]
 * instead sends the flow to manual entry, which works today and produces a true record.
 *
 * They are bound rather than left out so that resolving the port fails as a message on the
 * scan screen instead of a Koin crash on a screen with nothing to do with scanning. Swapping
 * in the real adapters is two lines of `coreDataModule`, the same way the remote data sources
 * are replaced by `supabaseModule`.
 */
internal class UnconfiguredBillExtractor : BillExtractor {
    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedBill> =
        DomainError.ScanUnavailable.left()
}

/** The document counterpart of [UnconfiguredBillExtractor], for the same reasons. */
internal class UnconfiguredDocumentExtractor : DocumentExtractor {
    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedDocument> =
        DomainError.ScanUnavailable.left()
}
