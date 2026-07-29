package com.hopcape.odo.core.domain.car.model

import kotlin.jvm.JvmInline

/**
 * A Vehicle Identification Number — the 17-character chassis number stamped on the car.
 *
 * Shared-kernel value object alongside [RegistrationNumber]: a plate can change hands and
 * be reissued, the VIN cannot, so it is the identity a buyer checks a record against.
 * Whichever surface captures or shows it — car setup, the garage's car card, a resale
 * passport — reads the same validated type rather than a raw String.
 *
 * Construct only via [of], which normalizes and validates: exactly [LENGTH] characters,
 * letters and digits only, and never I, O or Q — the standard omits those three so they
 * cannot be misread as 1 and 0. Invalid input yields `null` rather than a
 * [com.hopcape.odo.core.domain.shared.DomainError], because the VIN is optional
 * everywhere it appears — the same shape as [RegistrationNumber.of].
 *
 * Not persisted yet: the `cars` table has no `vin` column (DB_SCHEMA §9.3 is
 * authoritative), so today it lives only as long as the screen that captured it. The type
 * is here so that adding the column becomes a migration plus a field, rather than a
 * re-derivation of what a valid VIN is.
 */
@JvmInline
value class Vin private constructor(val value: String) {

    /** Grouped for reading: `MA3ERLF1S00123456` → `MA3ERLF1S 00123456` (WMI+VDS · VIS). */
    val formatted: String get() = value.take(SECTION_SPLIT) + " " + value.drop(SECTION_SPLIT)

    companion object {
        const val LENGTH: Int = 17

        /** Where the manufacturer/descriptor half ends and the serial half begins. */
        private const val SECTION_SPLIT = 9

        /** Characters the VIN standard excludes, to keep 1/I and 0/O/Q apart. */
        private val EXCLUDED = setOf('I', 'O', 'Q')

        fun of(raw: String?): Vin? {
            val normalized = raw?.uppercase()?.filter { it.isLetterOrDigit() } ?: return null
            return when {
                normalized.length != LENGTH -> null
                normalized.any { it in EXCLUDED } -> null
                else -> Vin(normalized)
            }
        }
    }
}
