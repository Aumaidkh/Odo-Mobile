package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.scan.PumpReadingExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedPumpReading
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock

/**
 * Reads a photographed pump display.
 *
 * Deliberately **without** the allowance check its bill and document siblings have, and it
 * spends no scan. Reading a pump is how a fill gets logged at all in a market with no UPI, or
 * by an owner paying cash — putting it behind the free-scan cap would meter the core act of
 * using the app, not a premium extra. Bills are different: they are read to extract a
 * workshop's line items, which is the Pro promise.
 *
 * There is no emptiness check either, because the extractor already refuses a frame with no
 * numbers in it. A partial read is a success here, unlike a bill: one number saves the owner
 * most of the typing, and the confirm step asks for whatever is missing.
 */
internal class ScanPumpDisplayUseCase(
    private val extractor: PumpReadingExtractor,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(storageKey: String): Either<DomainError, ExtractedPumpReading> =
        either {
            val image = ScannedImage(
                id = ScanId.new(ids),
                storageKey = storageKey,
                capturedAt = clock.now(),
            )
            extractor.extract(image).bind()
        }
}
