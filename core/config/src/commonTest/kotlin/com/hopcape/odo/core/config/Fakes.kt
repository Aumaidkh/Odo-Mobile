package com.hopcape.odo.core.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [ConfigSource] holding whatever the test put in it. Values are stored in the raw
 * string form a backend hands back, so a test can express "the console holds a value
 * that does not parse" the same way reality does.
 */
internal class FakeConfigSource(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : ConfigSource {

    private val _generation = MutableStateFlow(0L)
    override val generation: StateFlow<Long> = _generation

    /** Blank maps to null, which is the contract every real source has to honour. */
    private fun raw(key: String): String? = values[key]?.takeIf { it.isNotBlank() }

    override fun boolean(key: String): Boolean? = raw(key)?.toBooleanStrictOrNull()
    override fun int(key: String): Int? = raw(key)?.toIntOrNull()
    override fun long(key: String): Long? = raw(key)?.toLongOrNull()
    override fun double(key: String): Double? = raw(key)?.toDoubleOrNull()
    override fun string(key: String): String? = raw(key)

    /** Mimics a fetch that activated new values. */
    fun activate(vararg pairs: Pair<String, String>) {
        pairs.forEach { (key, value) -> values[key] = value }
        _generation.value += 1
    }
}

internal class FakeOverrides : LocalConfigOverrides {

    private val values = mutableMapOf<String, String>()
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val changes: Flow<Unit> = _changes

    override fun raw(key: String): String? = values[key]

    override fun set(key: String, raw: String) {
        values[key] = raw
        _changes.tryEmit(Unit)
    }

    override fun clear(key: String) {
        values.remove(key)
        _changes.tryEmit(Unit)
    }

    override fun clearAll() {
        values.clear()
        _changes.tryEmit(Unit)
    }
}

internal fun resolver(
    source: FakeConfigSource = FakeConfigSource(),
    overrides: LocalConfigOverrides? = null,
    contributions: List<ConfigContribution> = listOf(SampleConfigContribution),
): ConfigResolver = ConfigResolver(
    registry = ConfigRegistry(contributions),
    source = source,
    overrides = overrides,
)
