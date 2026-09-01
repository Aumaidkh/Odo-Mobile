package com.hopcape.odo.core.config

/**
 * The types a config key may have. A JSON-decoded object is deliberately absent —
 * see [Value].
 */
enum class ConfigType { BOOLEAN, INT, LONG, DOUBLE, STRING, ENUM }

/**
 * Everything known about one key at runtime: what it is called, what type it holds,
 * what it answers before anything else is known, and who to ask about it.
 *
 * KSP builds these from [Flag] and [Value] and collects them into a
 * [ConfigContribution] per module. [ConfigRegistry] assembles the contributions, and
 * the QA screen reads the result.
 *
 * [default] is already typed by the time it reaches here: `Boolean`, `Int`, `Long`,
 * `Double`, or `String`. The string form written at the declaration site is parsed
 * during code generation, not at runtime. An [ConfigType.ENUM] default is the name
 * of the constant, as a string.
 *
 * [range] is `"min..max"`, inclusive, and only ever set on a number.
 * [enumValues] is only set on an [ConfigType.ENUM] and lists the accepted names.
 */
data class ConfigKey(
    val key: String,
    val type: ConfigType,
    val default: Any,
    val owner: String,
    val why: String,
    val range: String? = null,
    val enumValues: List<String> = emptyList(),
) {
    init {
        require(KEY_FORMAT.matches(key)) {
            "Config key '$key' must match ${KEY_FORMAT.pattern}"
        }
        require(owner.isNotBlank()) { "Config key '$key' has no owner" }
        require(why.isNotBlank()) { "Config key '$key' has no why" }
    }

    companion object {
        /**
         * Lowercase, digits and underscores, starting with a letter. Enforced by the
         * processor at build time and again here, because a group can also be written
         * by hand.
         */
        val KEY_FORMAT = Regex("^[a-z][a-z0-9_]*$")
    }
}

/**
 * The keys one module declares. KSP generates one of these per [ConfigGroup].
 */
interface ConfigContribution {
    val groupName: String
    val keys: List<ConfigKey>
}

/** Which step of the resolution order answered a read. */
enum class ConfigValueSource { OVERRIDE, REMOTE, DEFAULT }

/**
 * One key's current answer, as text, with where it came from.
 *
 * Text rather than the typed value because the only caller is a screen that lists every
 * key side by side, and because an override is typed in as text anyway.
 */
data class ResolvedConfigValue(
    val key: ConfigKey,
    val value: String,
    val source: ConfigValueSource,
)
