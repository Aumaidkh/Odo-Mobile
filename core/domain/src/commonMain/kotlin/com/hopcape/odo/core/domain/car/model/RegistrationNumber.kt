package com.hopcape.odo.core.domain.car.model

import kotlin.jvm.JvmInline

/**
 * Registration number, stored normalized: uppercased with everything but `A`-`Z`/`0`-`9`
 * stripped (DB_SCHEMA §9.3). Optional — blank/absent input yields `null`.
 *
 * Stripping punctuation, not just whitespace, matters because this is the value the DB's
 * per-owner uniqueness constraint (`uq_cars_owner_reg`) compares — `"MH12AB1234"` and
 * `"MH-12-AB-1234"` name the same plate and must collide here, or two cars can exist for
 * one physical car. Matches `OdoRegistrationNumberField`'s own input filter
 * (`asRegistrationInput`), which never lets anything else through in the first place; this
 * is the backstop for a value that reached here some other way (a plate lookup, a future
 * import) without going through that field.
 */
@JvmInline
value class RegistrationNumber private constructor(val value: String) {
    companion object {
        fun of(raw: String?): RegistrationNumber? {
            val normalized = raw?.uppercase()?.filter { it in 'A'..'Z' || it in '0'..'9' }
            return if (normalized.isNullOrBlank()) null else RegistrationNumber(normalized)
        }
    }
}
