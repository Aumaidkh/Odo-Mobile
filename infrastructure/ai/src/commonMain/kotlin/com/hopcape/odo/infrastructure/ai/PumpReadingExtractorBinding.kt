package com.hopcape.odo.infrastructure.ai

import com.hopcape.odo.core.domain.scan.PumpReadingExtractor
import org.koin.core.scope.Scope

/**
 * Which [PumpReadingExtractor] this platform runs.
 *
 * The same shape as the bill and document bindings, and for the same reason: ML Kit ships an
 * Android SDK only. Android reads the display on-device; iOS answers unavailable, and the
 * scanner's own "enter the numbers myself" route is what carries owners from there into the
 * prefill form every market already relies on.
 */
internal expect fun Scope.platformPumpReadingExtractor(): PumpReadingExtractor
