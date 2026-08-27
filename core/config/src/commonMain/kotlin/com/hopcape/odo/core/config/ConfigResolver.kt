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
     *
     * Matching ignores case and the declared name is what comes back, because a remote
     * console holds `off` while the Kotlin constant is `OFF`. Generated code calls
     * `valueOf` on this, which is case-sensitive.
     */
    fun enumName(key: String): String {
        val descriptor = registry.require(key)
        check(descriptor.type == ConfigType.ENUM) {
            "Config key '$key' is ${descriptor.type}, read as ENUM"
        }
        val accepted = { raw: String? ->
            raw?.let { value -> descriptor.enumValues.firstOrNull { it.equals(value, ignoreCase = true) } }
        }
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

    /**
     * What [key] currently answers, and which step of the resolution order answered.
     *
     * The source is the point. "The flag is off" and "the flag is off *because the console
     * never set it and this is the compiled default*" are different bugs, and from a device
     * there is otherwise no way to tell them apart.
     */
    fun describe(key: String): ResolvedConfigValue {
        val descriptor = registry.require(key)
        val fromOverride = overrides?.raw(key)?.let { parse(descriptor, it) }
        if (fromOverride != null) {
            return ResolvedConfigValue(descriptor, fromOverride, ConfigValueSource.OVERRIDE)
        }
        val fromRemote = remote(descriptor)
        if (fromRemote != null) {
            return ResolvedConfigValue(descriptor, fromRemote, ConfigValueSource.REMOTE)
        }
        return ResolvedConfigValue(descriptor, descriptor.default.toString(), ConfigValueSource.DEFAULT)
    }

    /** Every registered key, in declaration order. What the QA screen lists. */
    fun describeAll(): List<ResolvedConfigValue> = registry.keys.map { describe(it.key) }

    /** A raw string as this key's type would read it, or null when it is not usable. */
    private fun parse(descriptor: ConfigKey, raw: String): String? = when (descriptor.type) {
        ConfigType.BOOLEAN -> raw.toBooleanStrictOrNull()?.toString()
        ConfigType.INT -> raw.toIntOrNull()?.takeIf { inRange(descriptor, it) }?.toString()
        ConfigType.LONG -> raw.toLongOrNull()?.takeIf { inRange(descriptor, it) }?.toString()
        ConfigType.DOUBLE -> raw.toDoubleOrNull()?.takeIf { inRange(descriptor, it) }?.toString()
        ConfigType.STRING -> raw
        ConfigType.ENUM -> descriptor.enumValues.firstOrNull { it.equals(raw, ignoreCase = true) }
    }

    private fun remote(descriptor: ConfigKey): String? = when (descriptor.type) {
        ConfigType.BOOLEAN -> source.boolean(descriptor.key)?.toString()
        ConfigType.INT -> source.int(descriptor.key)?.takeIf { inRange(descriptor, it) }?.toString()
        ConfigType.LONG -> source.long(descriptor.key)?.takeIf { inRange(descriptor, it) }?.toString()
        ConfigType.DOUBLE -> source.double(descriptor.key)?.takeIf { inRange(descriptor, it) }?.toString()
        ConfigType.STRING -> source.string(descriptor.key)
        ConfigType.ENUM -> source.string(descriptor.key)
            ?.let { raw -> descriptor.enumValues.firstOrNull { it.equals(raw, ignoreCase = true) } }
    }

    private fun inRange(descriptor: ConfigKey, value: Any): Boolean =
        ConfigRange.contains(descriptor.range, value)

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
