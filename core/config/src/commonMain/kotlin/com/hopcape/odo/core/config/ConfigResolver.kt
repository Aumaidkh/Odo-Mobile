package com.hopcape.odo.core.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart

/**
 * The one place a config value is decided. Generated implementations call nothing else.
 *
 * **Resolution order**, applied to every read:
 *
 * 1. [LocalConfigOverrides] — a per-device value set from the QA screen. Debug builds
 *    only, because release builds pass `null` here.
 * 2. [ConfigSource] — whatever the backend last activated.
 * 3. The compiled default on [ConfigKey].
 *
 * Step 3 is not a fallback of last resort. It is the normal answer for the first
 * seconds of every install's life, and the permanent answer on a device that never
 * reaches the backend.
 *
 * A value is skipped, and resolution continues to the next step, when it does not
 * parse, or when it falls outside the key's declared range. That second rule is why
 * [ConfigKey.range] is carried at runtime rather than only checked at build time: the
 * build can only vouch for the compiled default, and a typo in the remote console is
 * exactly the case worth surviving.
 */
class ConfigResolver(
    private val registry: ConfigRegistry,
    private val source: ConfigSource,
    private val overrides: LocalConfigOverrides? = null,
) {

    fun boolean(key: String): Boolean =
        resolve(key, ConfigType.BOOLEAN, String::toBooleanStrictOrNull, source::boolean)

    fun int(key: String): Int =
        resolve(key, ConfigType.INT, String::toIntOrNull, source::int)

    fun long(key: String): Long =
        resolve(key, ConfigType.LONG, String::toLongOrNull, source::long)

    fun double(key: String): Double =
        resolve(key, ConfigType.DOUBLE, String::toDoubleOrNull, source::double)

    fun string(key: String): String =
        resolve(key, ConfigType.STRING, { it }, source::string)

    /**
     * The name of the enum constant for [key], guaranteed to be one of the names the
     * declaration listed. A value that is not — a console typo, or a constant removed
     * in a later release — is skipped, so the generated code's `valueOf` cannot fail.
     */
    fun enumName(key: String): String {
        val descriptor = registry.require(key)
        check(descriptor.type == ConfigType.ENUM) {
            "Config key '$key' is ${descriptor.type}, read as ENUM"
        }
        val accepted = { name: String? -> name?.takeIf { it in descriptor.enumValues } }
        accepted(overrides?.raw(key))?.let { return it }
        accepted(source.string(key))?.let { return it }
        return descriptor.default as String
    }

    fun booleanFlow(key: String): Flow<Boolean> = observe { boolean(key) }

    fun intFlow(key: String): Flow<Int> = observe { int(key) }

    fun longFlow(key: String): Flow<Long> = observe { long(key) }

    fun doubleFlow(key: String): Flow<Double> = observe { double(key) }

    fun stringFlow(key: String): Flow<String> = observe { string(key) }

    fun enumNameFlow(key: String): Flow<String> = observe { enumName(key) }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolve(
        key: String,
        expected: ConfigType,
        parse: (String) -> T?,
        remote: (String) -> T?,
    ): T {
        val descriptor = registry.require(key)
        check(descriptor.type == expected) {
            "Config key '$key' is ${descriptor.type}, read as $expected"
        }
        val accepted = { value: T? -> value?.takeIf { ConfigRange.contains(descriptor.range, it) } }
        accepted(overrides?.raw(key)?.let(parse))?.let { return it }
        accepted(remote(key))?.let { return it }
        return descriptor.default as T
    }

    /**
     * Re-reads on every generation bump and on every override write, then drops
     * repeats. A fetch that activates values no consumer cares about therefore costs
     * one read per observed key and no emission.
     */
    private fun <T> observe(read: () -> T): Flow<T> {
        val overrideChanges = (overrides?.changes ?: emptyFlow()).onStart { emit(Unit) }
        return combine(source.generation, overrideChanges) { _, _ -> read() }
            .distinctUntilChanged()
    }
}
