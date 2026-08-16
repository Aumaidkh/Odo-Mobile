package com.hopcape.odo.core.domain.scan

import arrow.core.Either
import com.hopcape.odo.core.domain.scan.model.ExtractedPumpReading
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port that reads the three numbers off a fuel pump's display.
 *
 * The one capture channel that works in every market. A pump shows what was dispensed
 * whether the owner paid by card, by phone or in cash, and it shows it in the same three
 * quantities everywhere — so this replaces nothing market-specific and is available to
 * everyone.
 *
 * Failure is an [Either], not an exception. A pump display is a hard thing to photograph —
 * sunlight, glare, a seven-segment font no text recogniser was trained on — so an unreadable
 * frame is an ordinary outcome, and what it leads to (type the amount instead) is a path the
 * screen already has.
 */
fun interface PumpReadingExtractor {

    /**
     * Read [image].
     *
     * A partial answer is a success, not a failure: one number is enough to save the owner
     * most of the typing, and [ExtractedPumpReading] carries per-field confidence so the
     * confirm step can flag what to check. Only a frame with nothing usable in it at all
     * comes back as [DomainError.ScanUnreadable].
     */
    suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedPumpReading>
}
