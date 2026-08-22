package com.hopcape.odo.core.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A config group written by hand, in exactly the shape KSP will generate.
 *
 * This exists so the runtime contract is proven before any code generation does. It
 * is also the target the processor is written against: if the generated output stops
 * matching this file's shape, one of the two is wrong.
 *
 * Four pieces per group — the interface a consumer injects, its implementation, a
 * sibling holding the flows, and the registry contribution.
 */
@ConfigGroup(SampleConfigContribution.GROUP)
internal interface SampleConfig {

    @Flag(
        key = "sample_enabled",
        default = true,
        owner = "platform",
        why = "Proves a boolean key resolves",
    )
    val enabled: Boolean

    @Value(
        key = "sample_retry_count",
        default = "3",
        owner = "platform",
        why = "Proves a bounded number resolves",
        range = "0..10",
    )
    val retryCount: Int

    @Value(
        key = "sample_mode",
        default = "off",
        owner = "platform",
        why = "Proves a string-backed enum resolves",
    )
    val mode: SampleMode
}

/**
 * A string-backed enum. The wire name is what the console holds and what the registry
 * lists; the constant is what callers see.
 */
internal enum class SampleMode(val wire: String) {
    OFF("off"),
    DEGRADED("degraded"),
    ON("on"),
    ;

    companion object {
        fun ofWire(wire: String): SampleMode = entries.first { it.wire == wire }
    }
}

internal class SampleConfigImpl(private val resolver: ConfigResolver) : SampleConfig {
    override val enabled: Boolean get() = resolver.boolean(SampleConfigContribution.ENABLED)
    override val retryCount: Int get() = resolver.int(SampleConfigContribution.RETRY_COUNT)
    override val mode: SampleMode
        get() = SampleMode.ofWire(resolver.enumName(SampleConfigContribution.MODE))
}

internal class SampleConfigFlows(private val resolver: ConfigResolver) {
    val enabled: Flow<Boolean> = resolver.booleanFlow(SampleConfigContribution.ENABLED)
    val retryCount: Flow<Int> = resolver.intFlow(SampleConfigContribution.RETRY_COUNT)
    val mode: Flow<SampleMode> = resolver.enumNameFlow(SampleConfigContribution.MODE)
        .map { SampleMode.ofWire(it) }
}

internal object SampleConfigContribution : ConfigContribution {

    const val GROUP = "sample"
    const val ENABLED = "sample_enabled"
    const val RETRY_COUNT = "sample_retry_count"
    const val MODE = "sample_mode"

    override val groupName: String = GROUP

    override val keys: List<ConfigKey> = listOf(
        ConfigKey(
            key = ENABLED,
            type = ConfigType.BOOLEAN,
            default = true,
            owner = "platform",
            why = "Proves a boolean key resolves",
        ),
        ConfigKey(
            key = RETRY_COUNT,
            type = ConfigType.INT,
            default = 3,
            owner = "platform",
            why = "Proves a bounded number resolves",
            range = "0..10",
        ),
        ConfigKey(
            key = MODE,
            type = ConfigType.ENUM,
            default = SampleMode.OFF.wire,
            owner = "platform",
            why = "Proves a string-backed enum resolves",
            enumValues = SampleMode.entries.map { it.wire },
        ),
    )
}
