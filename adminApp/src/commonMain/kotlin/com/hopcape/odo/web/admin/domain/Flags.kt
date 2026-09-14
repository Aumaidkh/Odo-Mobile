package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/**
 * One remotely-set key.
 *
 * A row in `app_config`, which is an *override* rather than the key itself: keys are
 * declared in Kotlin with `@Flag`, and each carries a compiled default. A key with
 * no active row here resolves to that default. So [isActive] is not cosmetic —
 * parking a row is how a value is handed back to the build.
 */
data class FeatureFlag(
    val key: String,
    val value: String,
    val description: String,
    val owner: String,
    /** BOOLEAN | INT | LONG | DOUBLE | STRING | ENUM, matching `ConfigType`. */
    val valueType: String,
    val isActive: Boolean,
    val updatedAt: String,
) {
    val isBoolean: Boolean get() = valueType == BOOLEAN
    val isOn: Boolean get() = value.equals("true", ignoreCase = true)

    /**
     * True when the value cannot possibly parse as its declared type.
     *
     * Worth showing rather than leaving to the device to discover: a key set to
     * `ture` does not fail loudly on a phone, it silently resolves to the compiled
     * default, and the person who typed it has no way of knowing.
     */
    val isMalformed: Boolean
        get() = when (valueType) {
            BOOLEAN -> !value.equals("true", true) && !value.equals("false", true)
            "INT", "LONG" -> value.toLongOrNull() == null
            "DOUBLE" -> value.toDoubleOrNull() == null
            else -> false
        }

    companion object {
        const val BOOLEAN = "BOOLEAN"
    }
}

/**
 * Feature flags, from the `app_config` table.
 *
 * A table, not Firebase Remote Config. Remote Config's API authenticates with a
 * Google service-account private key that cannot live in a browser, so reaching it
 * meant an edge function holding the key, a key generated in one console and a role
 * granted in another. Here RLS decides who may write and `admin_audit()` records it,
 * like every other admin write in this panel.
 */
interface FlagsRepository {

    suspend fun flags(): Either<WebError, List<FeatureFlag>>

    /** Sets one key's value. The row must already exist — this panel does not invent keys. */
    suspend fun set(key: String, value: String): Either<WebError, Unit>

    /**
     * Parks or restores a key.
     *
     * Parked means the override stops applying and the app falls back to the
     * compiled default, while the description and last value stay for whoever asks
     * why it was ever set.
     */
    suspend fun setActive(key: String, active: Boolean): Either<WebError, Unit>
}
