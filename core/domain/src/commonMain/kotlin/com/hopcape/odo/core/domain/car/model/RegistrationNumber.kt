package com.hopcape.odo.core.domain.car.model

import kotlin.jvm.JvmInline

/**
 * Registration number, stored normalized: uppercased with all whitespace
 * removed (DB_SCHEMA §9.3). Optional — blank/absent input yields `null`.
 */
@JvmInline
value class RegistrationNumber private constructor(val value: String) {
    companion object {
        fun of(raw: String?): RegistrationNumber? {
            val normalized = raw?.filterNot { it.isWhitespace() }?.uppercase()
            return if (normalized.isNullOrBlank()) null else RegistrationNumber(normalized)
        }
    }
}
